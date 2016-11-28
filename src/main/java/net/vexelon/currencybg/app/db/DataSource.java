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
package net.vexelon.currencybg.app.db;

import java.io.Closeable;
import java.util.List;

import android.content.Context;
import net.vexelon.currencybg.app.db.models.CurrencyData;

/**
 * Encapsulates the available read-write operations to and from an underlying
 * data source implementation.
 */
public interface DataSource extends Closeable {

	/**
	 * Establishes connection to data source.
	 *
	 * @param context
	 * @throws DataSourceException
	 *             If an SQL error is thrown.
	 */
	void connect(Context context) throws DataSourceException;

	/**
	 * Adds exchange rates data.
	 *
	 * @param rates
	 * @throws DataSourceException
	 */
	public void addRates(List<CurrencyData> rates) throws DataSourceException;

	/**
	 * Returns all latest downloaded currencies from all sources.
	 *
	 * @return
	 * @throws DataSourceException
	 */
	public List<CurrencyData> getLastRates() throws DataSourceException;

	/**
	 * Returns all currencies for the specific source.
	 *
	 * @param source
	 * @return
	 * @throws DataSourceException
	 */
	public List<CurrencyData> getAllCurrencies(Integer source) throws DataSourceException;

	/**
	 * Returns all currencies for the specific code.
	 *
	 * @param code
	 * @return
	 * @throws DataSourceException
	 */
	public List<CurrencyData> getAllRates(String code) throws DataSourceException;

	/**
	 * Delete all rates older than {@code backDays}.
	 *
	 * @param backDays
	 * @throws DataSourceException
	 */
	public void deleteRates(int backDays) throws DataSourceException;
}
