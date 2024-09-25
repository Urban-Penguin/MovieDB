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
package com.wirelessalien.android.moviedb.activity

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.BaseActivity.Companion.getLanguageParameter
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.fragment.BaseFragment
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL

class FilmographyActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ShowBaseAdapter
    private lateinit var list: ArrayList<JSONObject>
    private lateinit var jsonArray: JSONArray

    private var API_KEY: String? = null

    private var mShowGenreList: HashMap<String, String?>? = null
    lateinit var preferences: SharedPreferences
    private var SHOWS_LIST_PREFERENCE = "key_show_shows_grid"
    private var GRID_SIZE_PREFERENCE = "key_grid_size_number"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filmography)
        mShowGenreList = HashMap()
        list = ArrayList()
        API_KEY = ConfigHelper.getConfigValue(applicationContext, "api_key")

        Thread.setDefaultUncaughtExceptionHandler { thread: Thread?, throwable: Throwable ->
            val crashLog = StringWriter()
            val printWriter = PrintWriter(crashLog)
            throwable.printStackTrace(printWriter)
            val osVersion = Build.VERSION.RELEASE
            var appVersion = ""
            try {
                appVersion = applicationContext.packageManager.getPackageInfo(
                    applicationContext.packageName,
                    0
                ).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            printWriter.write("\nDevice OS Version: $osVersion")
            printWriter.write("\nApp Version: $appVersion")
            printWriter.close()
            try {
                val fileName = "Crash_Log.txt"
                val targetFile = File(applicationContext.filesDir, fileName)
                val fileOutputStream = FileOutputStream(targetFile, true)
                fileOutputStream.write((crashLog.toString() + "\n").toByteArray())
                fileOutputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            Process.killProcess(Process.myPid())
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        recyclerView = findViewById(R.id.recyclerView)

        if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, false)) {
            recyclerView.layoutManager = GridLayoutManager(this, preferences.getInt(GRID_SIZE_PREFERENCE, 3))
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        if (intent.getBooleanExtra("callApi", false)) {
            fetchActorMovies()
        } else {
            // Get the list of movies from the intent
            jsonArray = JSONArray(intent.getStringExtra("movieArray"))

            for (i in 0 until jsonArray.length()) {
                val actorMovies = jsonArray.getJSONObject(i)
                list.add(actorMovies)
            }

        adapter = ShowBaseAdapter(list, mShowGenreList!!,  if (preferences.getBoolean(
                BaseFragment.SHOWS_LIST_PREFERENCE, true))
            ShowBaseAdapter.MView.GRID
        else ShowBaseAdapter.MView.FILMOGRAPHY,
            showDeleteButton = false)

            recyclerView.adapter = adapter
            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            progressBar.visibility = View.GONE
        }
    }

    private fun fetchActorMovies() {
        CoroutineScope(Dispatchers.Main).launch {
            val response = withContext(Dispatchers.IO) { doInBackground() }
            onPostExecute(response)
        }
    }

    private fun doInBackground(): String? {
        var line: String?
        val stringBuilder = StringBuilder()
        val actorId = intent.getStringExtra("actorId")

        // Load the webpage with the person's shows.
        try {
            val url = URL(
                "https://api.themoviedb.org/3/person/" +
                        actorId + "/combined_credits?api_key=" + API_KEY + getLanguageParameter(
                    applicationContext
                )
            )
            val urlConnection = url.openConnection()
            try {
                val bufferedReader = BufferedReader(
                    InputStreamReader(
                        urlConnection.getInputStream()
                    )
                )

                // Create one long string of the webpage.
                while (bufferedReader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }

                // Close connection and return the data from the webpage.
                bufferedReader.close()
                return stringBuilder.toString()
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }

        // Loading the dataset failed, return null.
        return null
    }

    private fun onPostExecute(response: String?) {
        if (!response.isNullOrEmpty()) {
            val reader = JSONObject(response)
            val type = intent.getStringExtra("type")
            var movieArray = JSONArray()

            movieArray = reader.getJSONArray(type)
            for (i in 0 until movieArray.length()) {
                val actorMovies = movieArray.getJSONObject(i)
                list.add(actorMovies)
            }

            adapter = ShowBaseAdapter(list, mShowGenreList!!,  if (preferences.getBoolean(
                    BaseFragment.SHOWS_LIST_PREFERENCE, true))
                ShowBaseAdapter.MView.GRID
            else ShowBaseAdapter.MView.FILMOGRAPHY,
                showDeleteButton = false)
            recyclerView.adapter = adapter


            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            progressBar.visibility = View.GONE
        }
    }
}