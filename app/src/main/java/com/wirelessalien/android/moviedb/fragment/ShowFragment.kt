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
package com.wirelessalien.android.moviedb.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContract
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.BaseActivity
import com.wirelessalien.android.moviedb.activity.FilterActivity
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.adapter.ShowBaseAdapter
import com.wirelessalien.android.moviedb.helper.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.Optional

class ShowFragment : BaseFragment() {
    private var API_KEY: String? = null
    private var mListType: String? = null
    override var mSearchView = false
    private var mSearchQuery: String? = null
    private var filterParameter = ""
    private var filterChanged = false

    private var visibleThreshold = 0
    private var currentPage = 0
    private var currentSearchPage = 0
    private var previousTotal = 0
    private var loading = true
    private var pastVisibleItems = 0
    private var visibleItemCount = 0
    private var totalItemCount = 0
    private var mShowListLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        API_KEY = ConfigHelper.getConfigValue(requireContext().applicationContext, "api_key")
        mListType = if (arguments != null) {
            requireArguments().getString(ARG_LIST_TYPE)
        } else {
            // Movie is the default case.
            SectionsPagerAdapter.MOVIE
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val gridSizePreference = preferences.getInt(GRID_SIZE_PREFERENCE, 3)
        visibleThreshold = gridSizePreference * gridSizePreference
        createShowList(mListType)
    }

    override fun doNetworkWork() {
        if (!mGenreListLoaded) {
            val handler = Handler(Looper.getMainLooper())
            val genreListThread = GenreListThread(mListType!!, handler)
            genreListThread.start()
        }
        if (!mShowListLoaded) {
            fetchShowList(arrayOf(mListType, "1"))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val fragmentView = inflater.inflate(R.layout.fragment_show, container, false)
        showShowList(fragmentView)
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.setImageResource(R.drawable.ic_filter_list)
        fab.isEnabled = true
        fab.setOnClickListener {
            // Start the FilterActivity
            filterRequestLauncher.launch(Intent())
        }
        return fragmentView
    }

    override fun onResume() {
        super.onResume()
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab.visibility = View.VISIBLE
        fab.setImageResource(R.drawable.ic_filter_list)
        fab.isEnabled = true
        fab.setOnClickListener {
            // Start the FilterActivity
            filterRequestLauncher.launch(Intent())
        }
    }

    private var filterRequestContract: ActivityResultContract<Intent, Boolean> =
        object : ActivityResultContract<Intent, Boolean>() {
            override fun createIntent(context: Context, input: Intent): Intent {
                return Intent(context, FilterActivity::class.java).putExtra("mode", mListType)
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
                return resultCode == Activity.RESULT_OK
            }
        }

    private var filterRequestLauncher = registerForActivityResult(
        filterRequestContract
    ) { result: Boolean ->
        if (result) {
            filterShows()
        }
    }

    /**
     * Filters the list of shows based on the preferences set in FilterActivity.
     */
    private fun filterShows() {
        // Get the parameters from the filter activity and reload the adapter
        val sharedPreferences = requireActivity().getSharedPreferences(
            FilterActivity.FILTER_PREFERENCES,
            Context.MODE_PRIVATE
        )
        var sortPreference: String?
        if (sharedPreferences.getString(FilterActivity.FILTER_SORT, null)
                .also { sortPreference = it } != null
        ) filterParameter = when (sortPreference) {
            "best_rated" -> "sort_by=vote_average.desc"
            "release_date" -> "sort_by=release_date.desc"
            "alphabetic_order" -> "sort_by=original_title.desc"
            else ->  // This will also be selected when 'most_popular' is checked.
                "sort_by=popularity.desc"
        }


        // Add the dates as constraints to the new API call.
        var datePreference: String?
        if (sharedPreferences.getString(FilterActivity.FILTER_DATES, null)
                .also { datePreference = it } != null
        ) {
            when (datePreference) {
                "in_theater" -> {
                    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val today = simpleDateFormat.format(Date())
                    val calendar = GregorianCalendar.getInstance()
                    calendar.time = Date()
                    calendar.add(Calendar.DAY_OF_YEAR, -31)
                    val monthAgo = simpleDateFormat.format(calendar.time)
                    filterParameter += ("&primary_release_date.gte=" + monthAgo
                            + "&primary_release_date.lte=" + today)
                }

                "between_dates" -> {
                    var startDate: String
                    if (sharedPreferences.getString(FilterActivity.FILTER_START_DATE, null)
                            .also { startDate = it!! } != null
                    ) {
                        filterParameter += "&primary_release_date.gte=$startDate"
                    }
                    var endDate: String
                    if (sharedPreferences.getString(FilterActivity.FILTER_END_DATE, null)
                            .also { endDate = it!! } != null
                    ) {
                        filterParameter += "&primary_release_date.lte=$endDate"
                    }
                }

                else -> {}
            }
        }

        // Add the genres to be included as constraints to the API call.
        val withGenres = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITH_GENRES,
                null
            ), ", "
        )
        if (withGenres != null && withGenres.isNotEmpty()) {
            filterParameter += "&with_genres="
            for (i in withGenres.indices) {
                filterParameter += withGenres[i]
                if (i + 1 != withGenres.size) {
                    filterParameter += ","
                }
            }
        }

        // Add the genres to be excluded as constraints to the API call.
        val withoutGenres = FilterActivity.convertStringToArrayList(
            sharedPreferences.getString(
                FilterActivity.FILTER_WITHOUT_GENRES,
                null
            ), ", "
        )
        if (withoutGenres != null && withoutGenres.isNotEmpty()) {
            filterParameter += "&without_genres="
            for (i in withoutGenres.indices) {
                filterParameter += withoutGenres[i]
                if (i + 1 != withoutGenres.size) {
                    filterParameter += ","
                }
            }
        }

        // Add keyword-IDs as the constraints to the API call.
        var withKeywords: String
        if (sharedPreferences.getString(FilterActivity.FILTER_WITH_KEYWORDS, "")
                .also { withKeywords = it!! } != ""
        ) {
            filterParameter += "&with_keywords=$withKeywords"
        }
        var withoutKeywords: String
        if (sharedPreferences.getString(FilterActivity.FILTER_WITHOUT_KEYWORDS, "")
                .also { withoutKeywords = it!! } != ""
        ) {
            filterParameter += "&without_keywords=$withoutKeywords"
        }
        filterChanged = true
        fetchShowList(arrayOf(mListType, "1"))
    }

    /**
     * Loads a list of shows from the API.
     *
     * @param mode determines if series or movies are retrieved.
     */
    private fun createShowList(mode: String?) {

        // Create a MovieBaseAdapter and load the first page
        mShowArrayList = ArrayList()
        mShowGenreList = HashMap()
        mShowAdapter = ShowBaseAdapter(
            mShowArrayList, mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, false), false
        )
        (requireActivity() as BaseActivity).checkNetwork()

        // Use persistent filtering if it is enabled.
        if (preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
            filterShows()
        }
    }

    /**
     * Visualises the list of shows on the screen.
     *
     * @param fragmentView the view to attach the ListView to.
     */
    override fun showShowList(fragmentView: View) {
        super.showShowList(fragmentView)

        // Dynamically load new pages when user scrolls down.
        mShowView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) { // Check for scroll down and if user is actively scrolling.
                    visibleItemCount = mShowLinearLayoutManager.childCount
                    totalItemCount = mShowLinearLayoutManager.itemCount
                    pastVisibleItems = mShowLinearLayoutManager.findFirstVisibleItemPosition()
                    if (loading) {
                        if (totalItemCount > previousTotal) {
                            loading = false
                            previousTotal = totalItemCount
                            if (mSearchView) {
                                currentSearchPage++
                            } else {
                                currentPage++
                            }
                        }
                    }
                    var threshold = visibleThreshold
                    if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
                        // It is a grid view, so the threshold should be bigger.
                        val gridSizePreference = preferences.getInt(GRID_SIZE_PREFERENCE, 3)
                        threshold = gridSizePreference * gridSizePreference
                    }

                    // When no new pages are being loaded,
                    // but the user is at the end of the list, load the new page.
                    if (!loading && visibleItemCount + pastVisibleItems + threshold >= totalItemCount) {
                        // Load the next page of the content in the background.
                        if (mSearchView) {
                            searchList(mListType, currentSearchPage, mSearchQuery, false)
                        } else {
                            // Check if the previous request returned any data
                            if (mShowArrayList.size > 0) {
                                fetchShowList(arrayOf(mListType, currentPage.toString()))
                            }
                        }
                        loading = true
                        currentPage++
                    }
                }
            }
        })
    }

    /**
     * Creates the ShowBaseAdapter with the (still empty) ArrayList.
     * Also starts an AsyncTask to load the items for the empty ArrayList.
     *
     * @param query the query to start the AsyncTask with (and that will be added to the
     * API call as search query).
     */
    fun search(query: String?) {
        // Create a separate adapter for the search results.
        mSearchShowArrayList = ArrayList()
        mSearchShowAdapter = ShowBaseAdapter(
            mSearchShowArrayList, mShowGenreList,
            preferences.getBoolean(SHOWS_LIST_PREFERENCE, false), false
        )

        // Cancel old AsyncTask if it exists.
        currentSearchPage = 1
        mSearchQuery = query
        searchList(mListType, 1, query, false)
    }

    /**
     * Sets the search variable to false and sets original adapter in the RecyclerView.
     */
    override fun cancelSearch() {
        mSearchView = false
        mShowView.adapter = mShowAdapter
        mShowAdapter.notifyDataSetChanged()
    }

    /**
     * Uses Thread to retrieve the list with popular shows.
     */
    private fun fetchShowList(params: Array<String?>) {
        CoroutineScope(Dispatchers.Main).launch {
            var missingOverview = false
            val listType: String?
            val page: Int

            try {
                if (!isAdded) {
                    return@launch
                }
                val progressBar = Optional.ofNullable(requireActivity().findViewById<ProgressBar>(R.id.progressBar))
                progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.VISIBLE }

                listType = params[0]
                page = params[1]!!.toInt()
                if (params.size > 2) {
                    missingOverview = params[2].equals("true", ignoreCase = true)
                }

                val response = withContext(Dispatchers.IO) {
                    try {
                        val api_key = ConfigHelper.getConfigValue(
                            requireContext().applicationContext,
                            "api_read_access_token"
                        )
                        val url: URL = if (missingOverview) {
                            URL(
                                "https://api.themoviedb.org/3/discover/$listType?$filterParameter&page=$page"
                            )
                        } else {
                            URL(
                                "https://api.themoviedb.org/3/discover/" + listType + "?" + filterParameter + "&page=" + page + BaseActivity.getLanguageParameter(context)
                            )
                        }
                        val client = OkHttpClient()
                        val request = Request.Builder()
                            .url(url)
                            .get()
                            .addHeader("Content-Type", "application/json;charset=utf-8")
                            .addHeader("Authorization", "Bearer $api_key")
                            .build()
                        client.newCall(request).execute().use { response ->
                            response.body()?.string()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        null
                    }
                }

                handleResponse(response, missingOverview, listType, page)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                val progressBar = Optional.ofNullable(requireActivity().findViewById<ProgressBar>(R.id.progressBar))
                progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.GONE }
            }
        }
    }

    private fun handleResponse(response: String?, missingOverview: Boolean, listType: String?, page: Int) {
        if (isAdded && !response.isNullOrEmpty()) {
            // Keep the user at the same position in the list.
            val position: Int = try {
                mShowLinearLayoutManager.findFirstVisibleItemPosition()
            } catch (npe: NullPointerException) {
                0
            }

            // If the filter has changed, remove the old items
            if (filterChanged) {
                mShowArrayList.clear()

                // Set the previous total back to zero.
                previousTotal = 0

                // Set filterChanged back to false.
                filterChanged = false
            }

            // Convert the JSON data from the webpage into JSONObjects
            val tempMovieArrayList = ArrayList<JSONObject>()
            try {
                val reader = JSONObject(response)
                val arrayData = reader.getJSONArray("results")
                for (i in 0 until arrayData.length()) {
                    val websiteData = arrayData.getJSONObject(i)
                    if (missingOverview) {
                        tempMovieArrayList.add(websiteData)
                    } else {
                        mShowArrayList.add(websiteData)
                    }
                }

                // Some translations might be lacking and need to be filled in.
                // Therefore, load the same list but in English.
                // After that, iterate through the translated list
                // and fill in any missing parts.
                if (Locale.getDefault().language != "en" && !missingOverview) {
                    fetchShowList(arrayOf(listType, page.toString(), "true"))
                }

                // If the overview is missing, add the overview from the English version.
                if (missingOverview) {
                    for (i in mShowArrayList.size - tempMovieArrayList.size until mShowArrayList.size) {
                        val movieObject = mShowArrayList[i]
                        if (movieObject.getString("overview") == "") {
                            movieObject.put(
                                "overview",
                                tempMovieArrayList[i - (mShowArrayList.size - tempMovieArrayList.size)].getString("overview")
                            )
                        }
                    }
                }

                // Reload the adapter (with the new page)
                // and set the user to his old position.
                mShowView.adapter = mShowAdapter
                if (page != 1) {
                    mShowView.scrollToPosition(position)
                }
                mShowListLoaded = true
            } catch (je: JSONException) {
                je.printStackTrace()
            }
        }
        loading = false
    }

    /**
     * Uses Thread to retrieve the list with shows that fulfill the search query
     * (and are of the requested type which means that nothing will turn up if you
     * search for a series in the movies tab (and there are no movies with the same name).
     */
    private fun searchList(
        listType: String?,
        page: Int,
        query: String?,
        missingOverview: Boolean
    ) {
        if (query.isNullOrEmpty()) {
            // If the query is empty, show the original show list
            cancelSearch()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            if (isAdded) {
                val progressBar = Optional.ofNullable(requireActivity().findViewById<ProgressBar>(R.id.progressBar))
                progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.VISIBLE }

                val response = withContext(Dispatchers.IO) {
                    var result: String? = null
                    try {
                        val url: URL = if (missingOverview) {
                            URL(
                                "https://api.themoviedb.org/3/search/" +
                                        listType + "?query=" + query + "&page=" + page +
                                        "&api_key=" + API_KEY
                            )
                        } else {
                            URL(
                                "https://api.themoviedb.org/3/search/" +
                                        listType + "?&query=" + query + "&page=" + page +
                                        "&api_key=" + API_KEY + BaseActivity.getLanguageParameter(context)
                            )
                        }
                        val urlConnection = url.openConnection()
                        val bufferedReader = BufferedReader(InputStreamReader(urlConnection.getInputStream()))
                        val stringBuilder = StringBuilder()
                        var line: String?
                        while (bufferedReader.readLine().also { line = it } != null) {
                            stringBuilder.append(line).append("\n")
                        }
                        bufferedReader.close()
                        result = stringBuilder.toString()
                    } catch (ioe: IOException) {
                        ioe.printStackTrace()
                    }
                    result
                }

                handleResponse(response, listType, page, query, missingOverview)
                progressBar.ifPresent { bar: ProgressBar -> bar.visibility = View.GONE }
            }
        }
    }

    private fun handleResponse(
        response: String?,
        listType: String?,
        page: Int,
        query: String?,
        missingOverview: Boolean
    ) {
        requireActivity().runOnUiThread {
            val position: Int = try {
                mShowLinearLayoutManager.findFirstVisibleItemPosition()
            } catch (npe: NullPointerException) {
                0
            }

            if (currentSearchPage <= 0) {
                mSearchShowArrayList.clear()
            }

            if (!response.isNullOrEmpty()) {
                val tempSearchMovieArrayList = ArrayList<JSONObject>()
                try {
                    val reader = JSONObject(response)
                    val arrayData = reader.getJSONArray("results")
                    for (i in 0 until arrayData.length()) {
                        val websiteData = arrayData.getJSONObject(i)
                        if (missingOverview) {
                            tempSearchMovieArrayList.add(websiteData)
                        } else {
                            mSearchShowArrayList.add(websiteData)
                        }
                    }

                    if (Locale.getDefault().language != "en" && !missingOverview) {
                        searchList(listType, page, query, true)
                    }
                    if (missingOverview) {
                        for (i in mSearchShowArrayList.indices) {
                            val movieObject = mSearchShowArrayList[i]
                            if (movieObject.getString("overview") == "") {
                                if (i < tempSearchMovieArrayList.size) {
                                    movieObject.put(
                                        "overview",
                                        tempSearchMovieArrayList[i].getString("overview")
                                    )
                                }
                            }
                        }
                    }

                    mSearchView = true
                    mShowView.adapter = mSearchShowAdapter
                    mShowView.scrollToPosition(position)
                } catch (je: JSONException) {
                    je.printStackTrace()
                }
            }
        }
    }

    companion object {
        private const val ARG_LIST_TYPE = "arg_list_type"
        fun newInstance(listType: String?): ShowFragment {
            val fragment = ShowFragment()
            val args = Bundle()
            args.putString(ARG_LIST_TYPE, listType)
            fragment.arguments = args
            return fragment
        }
    }
}
