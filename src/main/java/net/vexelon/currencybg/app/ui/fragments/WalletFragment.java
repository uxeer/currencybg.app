/*
 * CurrencyBG App
 * Copyright (C) 2016 Vexelon.NET Services
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.vexelon.currencybg.app.ui.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.clans.fab.FloatingActionButton;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import net.vexelon.currencybg.app.AppSettings;
import net.vexelon.currencybg.app.Defs;
import net.vexelon.currencybg.app.R;
import net.vexelon.currencybg.app.common.Sources;
import net.vexelon.currencybg.app.db.DataSource;
import net.vexelon.currencybg.app.db.DataSourceException;
import net.vexelon.currencybg.app.db.SQLiteDataSource;
import net.vexelon.currencybg.app.db.models.CurrencyData;
import net.vexelon.currencybg.app.db.models.WalletEntry;
import net.vexelon.currencybg.app.db.models.WalletEntryInvestment;
import net.vexelon.currencybg.app.ui.components.CalculatorWidget;
import net.vexelon.currencybg.app.ui.components.ConvertSourceListAdapter;
import net.vexelon.currencybg.app.ui.components.WalletListAdapter;
import net.vexelon.currencybg.app.ui.events.LoadListener;
import net.vexelon.currencybg.app.ui.utils.CurrencyCodes;
import net.vexelon.currencybg.app.ui.utils.UIUtils;
import net.vexelon.currencybg.app.utils.DateTimeUtils;
import net.vexelon.currencybg.app.utils.IOUtils;
import net.vexelon.currencybg.app.utils.NumberUtils;
import net.vexelon.currencybg.app.utils.StringUtils;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.LocalDateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WalletFragment extends AbstractFragment
		implements LoadListener<Pair<Multimap<String, CurrencyData>, List<WalletEntry>>>,
		TimePickerDialog.OnTimeSetListener, DatePickerDialog.OnDateSetListener {

	private ListView walletListView;
	private TextView dateTimeView;
	private WalletListAdapter walletListAdapter;
	private LocalDateTime dateTimeSelected;
	private String codeSelected = "";
	private Multimap<String, CurrencyData> currencyCodeToData;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.rootView = inflater.inflate(R.layout.fragment_wallet, container, false);
		init(rootView, inflater);
		return rootView;
	}

	private void init(View view, LayoutInflater inflater) {
		walletListView = view.findViewById(R.id.list_wallet_entries);

		walletListView.setOnItemLongClickListener((AdapterView<?> parent, View v, int position, long id) -> {
			WalletListAdapter adapter = (WalletListAdapter) walletListView.getAdapter();

			WalletEntry removed = adapter.remove(position);
			if (removed != null) {
				adapter.notifyDataSetChanged();

				try (DataSource source = new SQLiteDataSource()) {
					source.connect(getActivity());
					source.deleteWalletEntry(removed.getId());

					showSnackbar(getActivity().getString(R.string.action_wallet_removed,
							NumberUtils.getCurrencyFormat(new BigDecimal(removed.getAmount()), removed.getCode())));

					vibrate(Defs.VIBRATE_DEL_DURATION);
				} catch (DataSourceException | IOException e) {
					Log.e(Defs.LOG_TAG, "Could not remove wallet entries from database!", e);
					showSnackbar(R.string.error_db_remove, Defs.TOAST_ERR_DURATION, true);
				}
			}

			return false;
		});
		walletListView.setOnItemClickListener((AdapterView<?> parent, View v, int position, long id) -> {
			if (R.id.wallet_row_icon == id || R.id.wallet_row_code == id) {
				showSnackbar(getActivity().getString(R.string.hint_currency_remove));
			} else {
				newInvestmentInfoDialog(walletListAdapter.getItem(position)).show();
			}
		});

		// add button
		FloatingActionButton action = view.findViewById(R.id.fab_wallet_entry);
		// action.attachToListView(walletListView);
		action.setOnClickListener((View v) -> {
			newAddWalletEntryDialog().show();
		});
	}

	@Override
	public void onResume() {
		/*
		 * Back from Settings or another activity, so we reload all currencies.
		 */
		updateUI();
		super.onResume();
	}

	// @Override
	// public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	// inflater.inflate(R.menu.wallet, menu);
	// super.onCreateOptionsMenu(menu, inflater);
	// }

	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		super.setUserVisibleHint(isVisibleToUser);
		if (isVisibleToUser) {
			/*
			 * Back from Currencies fragment view, so we reload all currencies. The user
			 * might have updated them.
			 */
			updateUI();
		}
	}

	private void updateUI() {
		new ReloadEntriesTask(getActivity(), this).execute();
	}

	/**
	 * Displays wallet asset investment margins
	 *
	 * @param entry
	 * @return
	 */
	private MaterialDialog newInvestmentInfoDialog(final WalletEntry entry) {
		final Context context = getActivity();
		final AppSettings appSettings = new AppSettings(context);

		final MaterialDialog dialog = new MaterialDialog.Builder(context)
				.title(getResources().getString(R.string.wallet_profit_details, entry.getCode())).cancelable(true)
				.autoDismiss(false).positiveText(R.string.text_ok)
				.onPositive((@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) -> {
					materialDialog.dismiss();
				}).customView(R.layout.dialog_details, true).build();

		dialog.setOnShowListener((DialogInterface dialogInterface) -> {
			View v = dialog.getCustomView();
			UIUtils.setText(v, R.id.details_header, getResources().getString(R.string.wallet_text_rates_details,
					CurrencyCodes.getCurrencyName(context.getResources(), entry.getCode())), true);

			Collection<CurrencyData> currencyDatas = currencyCodeToData.get(entry.getCode());

			/**
			 * Calculate investments for each source found
			 */
			List<WalletEntryInvestment> investments = Lists.newArrayListWithCapacity(currencyDatas.size());
			for (CurrencyData currencyData : currencyDatas) {
				investments.add(getInvestment(entry, currencyData));
			}

			/**
			 * Sort investments by profit
			 */
			Collections.sort(investments, (WalletEntryInvestment i1, WalletEntryInvestment i2) -> {
				BigDecimal p1 = i1.getInvestmentMargin();
				BigDecimal p2 = i2.getInvestmentMargin();
				return p1.subtract(p2).compareTo(BigDecimal.ZERO) > 0 ? -1 : 1;
			});

			/*
			 * Prepare display results
			 */
			if (!investments.isEmpty()) {
				int precisionMode = appSettings.getCurrenciesPrecision();

				StringBuilder buffer = new StringBuilder();

				// paint the first in either green or red depending on
				// profit margin
				WalletEntryInvestment top = Iterables.getFirst(investments, null);
				formatInvestment(context, top, true, precisionMode, buffer);
				investments.remove(top);

				for (WalletEntryInvestment next : investments) {
					formatInvestment(context, next, false, precisionMode, buffer);
				}

				UIUtils.setText(v, R.id.details_content, buffer.toString(), true);
			}
		});

		return dialog;
	}

	/**
	 * Calculates investment margins and all kind of investment infos for a given
	 * wallet entry and source rate
	 *
	 * @param walletEntry
	 * @param currencyData
	 * @return
	 */
	private WalletEntryInvestment getInvestment(WalletEntry walletEntry, CurrencyData currencyData) {
		WalletEntryInvestment investment = new WalletEntryInvestment(walletEntry, currencyData);

		MathContext mathContext = NumberUtils.getCurrencyMathContext();
		BigDecimal hundred = new BigDecimal("100.0", mathContext);

		// bug BGN and get up-to-date values
		investment.setInitialValue(NumberUtils.buyCurrency(walletEntry.getAmount(), walletEntry.getPurchaseRate(), 1));
		investment.setCurrentValue(
				NumberUtils.buyCurrency(walletEntry.getAmount(), currencyData.getBuy(), currencyData.getRatio()));

		investment.setInvestmentMargin(NumberUtils.getProfit(walletEntry.getAmount(), walletEntry.getPurchaseRate(), 1,
				currencyData.getBuy(), currencyData.getRatio()));

		BigDecimal subtract = investment.getCurrentValue().subtract(investment.getInitialValue());
		subtract = subtract.divide(investment.getInitialValue(), mathContext);
		investment.setInvestmentMarginPercentage(subtract.multiply(hundred, mathContext));

		DateTime boughtAt = new DateTime(walletEntry.getPurchaseTime());
		DateTime soldAt = new DateTime(DateTimeUtils.parseStringToDate(currencyData.getDate()));
		int days = (int) new Duration(boughtAt.getMillis(), soldAt.getMillis()).getStandardDays();
		investment.setInvestmentDuration(days > 0 ? days : 1);

		investment.setDailyChange(investment.getInvestmentMargin()
				.divide(new BigDecimal(investment.getInvestmentDuration(), mathContext), mathContext));
		investment.setDailyChangePercentage(investment.getDailyChange().divide(hundred, mathContext));

		return investment;
	}

	private void formatInvestment(Context context, WalletEntryInvestment investment, boolean isTop, int precisionMode,
			StringBuilder output) {

		// profit info
		output.append("<b>").append(colorfyProfit(investment.getInvestmentMargin(), isTop, precisionMode))
				.append("</b><br>");

		// source info
		output.append("&emsp;").append(getResources().getString(R.string.wallet_text_at_source,
				Sources.getFullName(context, investment.getCurrencyData().getSource()))).append("<br>");
		output.append("&emsp;")
				.append(getResources().getString(R.string.wallet_text_on_date,
						DateTimeUtils.toDateTimeText(context,
								DateTimeUtils.parseStringToDate(investment.getCurrencyData().getDate()))))
				.append("<br><br>");

		// margins info
		output.append("&emsp;").append(getResources().getString(R.string.wallet_text_initial_price,
				colorfyProfit(investment.getInitialValue(), false, precisionMode))).append("<br>");
		output.append("&emsp;").append(getResources().getString(R.string.wallet_text_current_price,
				colorfyProfit(investment.getCurrentValue(), false, precisionMode)));
		output.append("<br>");
		output.append("&emsp;").append(getResources().getString(R.string.wallet_text_profit_margin,
				NumberUtils.scaleNumber(investment.getInvestmentMarginPercentage(), Defs.SCALE_SHOW_LONG)));
		output.append("<br>");
		output.append("&emsp;").append(getResources().getString(R.string.wallet_text_duration,
				Integer.toString(investment.getInvestmentDuration())));
		output.append("<br>");
		output.append("&emsp;").append(getResources().getString(R.string.wallet_text_daily_change,
				colorfyProfit(investment.getDailyChange(), isTop, precisionMode)));
		output.append("<br>");
		output.append("&emsp;").append(getResources().getString(R.string.wallet_text_daily_change_perc,
				NumberUtils.scaleNumber(investment.getDailyChangePercentage(), Defs.SCALE_SHOW_LONG)));
		output.append("<br><br>");
	}

	private String colorfyProfit(BigDecimal amount, boolean isTop, int precisionMode) {
		int result = amount.compareTo(BigDecimal.ZERO);

		if (result > 0 && isTop) {
			return UIUtils.toHtmlColor(formatCurrency(amount, Defs.CURRENCY_CODE_BGN, precisionMode),
					Defs.COLOR_OK_GREEN);
		} else if (result < 0) {
			return UIUtils.toHtmlColor(formatCurrency(amount, Defs.CURRENCY_CODE_BGN, precisionMode),
					Defs.COLOR_DANGER_RED);
		}

		return formatCurrency(amount, Defs.CURRENCY_CODE_BGN, precisionMode);
	}

	/**
	 * Displays add wallet entry dialog
	 *
	 * @return
	 */
	private MaterialDialog newAddWalletEntryDialog() {
		final Context context = getActivity();

		final MaterialDialog dlg = new MaterialDialog.Builder(context).title(R.string.action_addwalletentry)
				.cancelable(false).customView(R.layout.walletentry_layout, true).autoDismiss(false)
				.positiveText(R.string.text_ok)
				.onPositive((@NonNull MaterialDialog dialog, @NonNull DialogAction which) -> {

					// validate and save new wallet entry
					final TextView amountView = dialog.getView().findViewById(R.id.wallet_entry_amount);
					TextView boughtAtView = dialog.getView().findViewById(R.id.wallet_entry_bought_at);
					TextView boughtOnView = dialog.getView().findViewById(R.id.wallet_entry_bought_on);

					String amount = amountView.getText().toString();
					String boughtAt = boughtAtView.getText().toString();
					String boughtOn = boughtOnView.getText().toString();

					// validation
					if (amount.isEmpty() || boughtAt.isEmpty() || boughtOn.isEmpty()) {
						showSnackbar(dialog.getView(), R.string.error_add_wallet_entry, Defs.TOAST_ERR_DURATION, true);
						return;
					}

					WalletEntry entry = new WalletEntry();
					try {
						entry.setCode(codeSelected);
						entry.setAmount(new BigDecimal(amount).toPlainString());
						entry.setPurchaseRate(new BigDecimal(boughtAt).toPlainString());
						entry.setPurchaseRatio(1);
						entry.setPurchaseTime(dateTimeSelected.toDate());
					} catch (NumberFormatException e) {
						Log.w(Defs.LOG_TAG, "Error reading amount of rate!", e);
						showSnackbar(dialog.getView(), R.string.error_add_wallet_entry, Defs.TOAST_ERR_DURATION, true);
						return;
					}

					// all ok
					dialog.dismiss();

					try (DataSource source = new SQLiteDataSource()) {
						source.connect(dialog.getContext());
						source.addWalletEntry(entry);
					} catch (DataSourceException | IOException e) {
						Log.e(Defs.LOG_TAG, "Could not save wallet entry to database!", e);
						showSnackbar(R.string.error_db_save, Defs.TOAST_ERR_DURATION, true);
					}

					// reload currencies
					updateUI();

				}).negativeText(R.string.text_cancel)
				.onNegative((@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) -> {
					materialDialog.dismiss();
				}).build();

		dlg.setOnShowListener((DialogInterface dialogInterface) -> {
			final View v = dlg.getCustomView();

			// setup source currencies
			final Spinner spinner = (Spinner) v.findViewById(R.id.wallet_entry_currency);
			spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					CurrencyData currency = (CurrencyData) parent.getSelectedItem();
					codeSelected = currency.getCode();
					UIUtils.setText(v, R.id.wallet_entry_bought_at, currency.getSell());
				}

				public void onNothingSelected(android.widget.AdapterView<?> parent) {
					// do nothing
				}
			});

			// assumes currencies are already loaded!
			List<CurrencyData> currencies = Lists.newArrayList();
			if (currencyCodeToData != null) {
				currencies.addAll(currencyCodeToData.values());
			}

			ConvertSourceListAdapter adapter = new ConvertSourceListAdapter(context,
					android.R.layout.simple_spinner_item, currencies);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			spinner.setAdapter(adapter);

			// source amount calculator
			final TextView amountView = dlg.getView().findViewById(R.id.wallet_entry_amount);
			amountView.setOnClickListener((View view) -> {
				new CalculatorWidget(context).showCalculator(
						StringUtils.defaultIfEmpty(amountView.getText().toString(), "0"), (BigDecimal value) -> {
							amountView.setText(value.toPlainString());
						});
			});

			// source rate calculator
			final TextView boughtAtView = dlg.getView().findViewById(R.id.wallet_entry_bought_at);
			boughtAtView.setOnClickListener((View view) -> {
				new CalculatorWidget(context).showCalculator(
						StringUtils.defaultIfEmpty(boughtAtView.getText().toString(), "0"), (BigDecimal value) -> {
							boughtAtView.setText(value.toPlainString());
						});
			});

			dateTimeSelected = LocalDateTime.now();
			dateTimeView = v.findViewById(R.id.wallet_entry_bought_on);
			dateTimeView.setText(DateTimeUtils.toDateTimeText(v.getContext(), dateTimeSelected.toDate()));

			v.findViewById(R.id.wallet_entry_bought_on).setOnClickListener((View view) -> {
				LocalDateTime dateTime = LocalDateTime.now();
				DatePickerDialog datePicker = DatePickerDialog.newInstance(WalletFragment.this, dateTime.getYear(),
						dateTime.getMonthOfYear() - 1, dateTime.getDayOfMonth());
				datePicker.setThemeDark(true);
				datePicker.show(getFragmentManager(), getResources().getText(R.string.text_pick_date).toString());
			});
		});

		return dlg;
	}

	@Override
	public void onDateSet(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth) {
		dateTimeSelected = dateTimeSelected.withYear(year).withMonthOfYear(monthOfYear + 1).withDayOfMonth(dayOfMonth);
		// show time picker
		TimePickerDialog timePicker = TimePickerDialog.newInstance(WalletFragment.this, dateTimeSelected.getHourOfDay(),
				dateTimeSelected.getMinuteOfHour(), true);
		timePicker.setThemeDark(true);
		timePicker.show(getFragmentManager(), getResources().getText(R.string.text_pick_time).toString());
	}

	@Override
	public void onTimeSet(TimePickerDialog view, int hourOfDay, int minute, int second) {
		dateTimeSelected = dateTimeSelected.withHourOfDay(hourOfDay).withMinuteOfHour(minute)
				.withSecondOfMinute(second);
		dateTimeView.setText(DateTimeUtils.toDateTimeText(view.getActivity(), dateTimeSelected.toDate()));
	}

	@Override
	public void onLoadStart() {
		setRefreshActionButtonState(true);
	}

	@Override
	public void onLoadSuccessful(@NonNull Supplier<Pair<Multimap<String, CurrencyData>, List<WalletEntry>>> newData) {
		setRefreshActionButtonState(false);

		final Activity activity = getActivity();
		if (activity != null) {
			final AppSettings appSettings = new AppSettings(activity);

			currencyCodeToData = newData.get().first;

			walletListAdapter = new WalletListAdapter(activity, android.R.layout.simple_spinner_item,
					newData.get().second, currencyCodeToData, appSettings.getCurrenciesPrecision());
			walletListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			walletListView.setAdapter(walletListAdapter);

			if (!newData.get().second.isEmpty()) {
				showSnackbar(R.string.wallet_message_reloaded);
			}
		}
	}

	@Override
	public void onLoadFailed(int msgId) {
		setRefreshActionButtonState(false);
		showSnackbar(msgId, Defs.TOAST_ERR_DURATION, true);
	}

	private static class ReloadEntriesTask extends AsyncTask<Void, Void, List<WalletEntry>> {

		private Activity activity;
		private LoadListener<Pair<Multimap<String, CurrencyData>, List<WalletEntry>>> listener;
		private Multimap<String, CurrencyData> codeToData;
		private int msgId = -1;

		public ReloadEntriesTask(Activity activity,
				LoadListener<Pair<Multimap<String, CurrencyData>, List<WalletEntry>>> listener) {
			this.activity = activity;
			this.listener = listener;
		}

		@Override
		protected void onPreExecute() {
			listener.onLoadStart();
		}

		@Override
		protected List<WalletEntry> doInBackground(Void... params) {
			Log.v(Defs.LOG_TAG, "Reloading wallet entries..");

			List<WalletEntry> entries = Lists.newArrayList();

			DataSource source = null;
			try {
				source = new SQLiteDataSource();
				source.connect(activity);
				entries.addAll(source.getWalletEntries());

				codeToData = AbstractFragment.getCurrenciesMapped(AbstractFragment.getVisibleCurrencies(activity,
						AbstractFragment.getCurrencies(activity, true)));

			} catch (DataSourceException e) {
				msgId = R.string.error_db_load;
				Log.e(Defs.LOG_TAG, "Could not load wallet entries from database!", e);
			} finally {
				IOUtils.closeQuitely(source);
			}

			return entries;
		}

		@Override
		protected void onPostExecute(final List<WalletEntry> entries) {
			if (msgId != -1) {
				listener.onLoadFailed(msgId);
			} else {
				listener.onLoadSuccessful(() -> Pair.create(codeToData, entries));
			}
		}

	}
}