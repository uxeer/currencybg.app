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
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.collect.Lists;

import net.vexelon.currencybg.app.Defs;
import net.vexelon.currencybg.app.R;
import net.vexelon.currencybg.app.ui.components.InfoListAdapter;

import java.util.List;

public class InfoFragment extends AbstractFragment {

	private static final String URL_3RDPARTY_LIBS = "intr://3rd";
	private static final String URL_3RDPARTY_ICONS = "intr://3rd_icons";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.rootView = inflater.inflate(R.layout.fragment_info, container, false);
		init(rootView);
		return rootView;
	}

	private void init(View view) {
		final Activity activity = getActivity();
		ListView lvInfo = (ListView) view.findViewById(R.id.list_info);
		final InfoListAdapter adapter = new InfoListAdapter(getActivity(), R.layout.info_row, getInfosList());
		lvInfo.setAdapter(adapter);
		lvInfo.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				String url = adapter.getUrl(position);
				if (URL_3RDPARTY_LIBS.equals(url)) {
					new MaterialDialog.Builder(getActivity()).customView(R.layout.fragment_thirdparty_libs, true)
							.positiveText(R.string.text_ok).build().show();
				} else if (URL_3RDPARTY_ICONS.equals(url)) {
					new MaterialDialog.Builder(getActivity()).customView(R.layout.fragment_thirdparty_icons, true)
							.positiveText(R.string.text_ok).build().show();
				} else if (url != null) {
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(browserIntent);
				}
			}
		});
	}

	private List<InfoListAdapter.InfoItem> getInfosList() {
		List<InfoListAdapter.InfoItem> infoList = Lists.newArrayList();

		PackageInfo packageInfo = null;
		try {
			packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(),
					PackageManager.GET_GIDS);
			infoList.add(newInfoRow(getString(R.string.about_version), packageInfo.versionName));
		} catch (Exception e) {
			Log.e(Defs.LOG_TAG, "", e);
		}

		infoList.add(newInfoRow(getString(R.string.about_issue_appdev), getString(R.string.about_issue_appdev_text),
				"https://github.com/vexelon-dot-net/currencybg.app/issues"));
		infoList.add(newInfoRow(getString(R.string.about_join_appdev), getString(R.string.about_join_appdev_text),
				"https://github.com/vexelon-dot-net/currencybg.app"));
		infoList.add(newInfoRow(getString(R.string.about_author), getString(R.string.about_author_text),
				"https://github.com/vexelon-dot-net/currencybg.app/blob/master/CREDITS"));
		infoList.add(newInfoRow(getString(R.string.about_logo), getString(R.string.about_logo_text),
				"http://www.stremena.com"));
		infoList.add(newInfoRow(getString(R.string.about_3rdparty_icons), "", URL_3RDPARTY_ICONS));
		infoList.add(newInfoRow(getString(R.string.about_3rdparty), "", URL_3RDPARTY_LIBS));

		return infoList;
	}

	private InfoListAdapter.InfoItem newInfoRow(String name, String value, String url) {
		return new InfoListAdapter.InfoItem(name, value, url);
	}

	private InfoListAdapter.InfoItem newInfoRow(String name, String value) {
		return newInfoRow(name, value, null);
	}
}
