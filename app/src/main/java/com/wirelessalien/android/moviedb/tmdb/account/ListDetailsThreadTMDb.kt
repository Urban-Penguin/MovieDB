/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.wirelessalien.android.moviedb.tmdb.account

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ListDetailsThreadTMDb(
    private val listId: Int,
    private val context: Context?,
    private val listener: OnFetchListDetailsListener
) {
    private val accessToken: String?

    init {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        accessToken = preferences.getString("access_token", "")
    }

    suspend fun fetchListDetails(page: Int) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.themoviedb.org/4/list/$listId?page=$page")
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            Log.d("ListDetailsThreadTMDb", "fetchListDetails: $request")
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val responseBody = withContext(Dispatchers.IO) {
                response.body()!!.string()
            }
            val jsonResponse = JSONObject(responseBody)
            val items = jsonResponse.getJSONArray("results")
            val listDetailsData = ArrayList<JSONObject>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                listDetailsData.add(item)
            }
            if (context is Activity) {
                context.runOnUiThread { listener.onFetchListDetails(listDetailsData) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    interface OnFetchListDetailsListener {
        fun onFetchListDetails(listDetailsData: ArrayList<JSONObject>?)
    }
}