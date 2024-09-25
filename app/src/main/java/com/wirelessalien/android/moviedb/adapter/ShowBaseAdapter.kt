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
package com.wirelessalien.android.moviedb.adapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.DetailActivity
import com.wirelessalien.android.moviedb.helper.MovieDatabaseHelper
import com.wirelessalien.android.moviedb.tmdb.account.DeleteFromListThreadTMDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ShowBaseAdapter(
    showList: ArrayList<JSONObject>,
    genreList: HashMap<String, String?>?, private val mGridView: MView, private val showDeleteButton: Boolean
) : RecyclerView.Adapter<ShowBaseAdapter.ShowItemViewHolder?>() {
    private val mShowArrayList: ArrayList<JSONObject>
    private var mGenreHashMap: HashMap<String, String?>? = null
    private var genreType: String? = null
    enum class MView {
        GRID,
        LIST,
        RECOMMENDATIONS,
        ROLES,
        FILMOGRAPHY
    }

    init {
        // Get the right "type" of genres.
        genreType = when (genreType) {
            null, "2" -> {
                null
            }
            "1" -> {
                SectionsPagerAdapter.MOVIE
            }
            else -> {
                SectionsPagerAdapter.TV
            }
        }
        mShowArrayList = showList
        if (genreList != null) {
            mGenreHashMap = genreList
        }
    }

    override fun getItemCount(): Int {
        // Return the amount of items in the list.
        return mShowArrayList.size
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShowItemViewHolder {
        val view: View = when (mGridView) {
            MView.GRID -> {
                LayoutInflater.from(parent.context).inflate(R.layout.show_grid_card, parent, false)
            }
            MView.LIST -> {
                LayoutInflater.from(parent.context).inflate(R.layout.show_card, parent, false)
            }
            MView.RECOMMENDATIONS -> {
                LayoutInflater.from(parent.context).inflate(R.layout.movie_card, parent, false)
            }
            MView.ROLES -> {
                LayoutInflater.from(parent.context).inflate(R.layout.role_card, parent, false)
            }
            else -> {
                LayoutInflater.from(parent.context).inflate(R.layout.filmography_card, parent, false)
            }
        }
        return ShowItemViewHolder(view, mGridView)
    }

    override fun onBindViewHolder(holder: ShowItemViewHolder, position: Int) {
        // Fill the views with the needed data.
        val showData = mShowArrayList[position]
        val context = holder.showView.context

        // Fills the views with show details.
        try {
            // Load the thumbnail with Picasso.
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val loadHDImage = defaultSharedPreferences.getBoolean(HD_IMAGE_SIZE, false)
            val imageSize = if (loadHDImage) "w780" else "w500"

            if (mGridView == MView.GRID || mGridView == MView.LIST || mGridView == MView.FILMOGRAPHY) {
                if (showData.getString(KEY_POSTER) == "null") {
                    holder.showImage.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            context.resources,
                            R.drawable.ic_broken_image,
                            null
                        )
                    )
                } else {
                    Picasso.get().load(
                        "https://image.tmdb.org/t/p/$imageSize" + showData.getString(
                            KEY_POSTER
                        )
                    ).into(holder.showImage)
                }
            } else {
                if (showData.getString(KEY_IMAGE) == "null" && showData.getString(KEY_POSTER) == "null"){
                    holder.showImage.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            context.resources,
                            R.drawable.ic_broken_image,
                            null
                        )
                    )
                } else if (showData.getString(KEY_IMAGE) != "null") {
                    Picasso.get().load(
                        "https://image.tmdb.org/t/p/$imageSize" + showData.getString(
                            KEY_IMAGE
                        )
                    ).into(holder.showImage)
                } else {
                    Picasso.get().load(
                        "https://image.tmdb.org/t/p/$imageSize" + showData.getString(
                            KEY_POSTER
                        )
                    ).into(holder.showImage)
                }
            }

            // Check if the object has "title" if not,
            // it is a series and "name" is used.
            val name =
                if (showData.has(KEY_TITLE)) showData.getString(KEY_TITLE) else showData.getString(
                    KEY_NAME
                )

            // Set the title and description.
            holder.showTitle.text = name

            // Set the right category color if available.
            if (mGridView == MView.GRID || mGridView == MView.LIST) {
                if (showData.has(KEY_CATEGORIES)) {
                    val categoryText = when (showData.getInt(KEY_CATEGORIES)) {
                        0 -> "Plan to watch"
                        1 -> "Watched"
                        2 -> "Watching"
                        3 -> "On hold"
                        4 -> "Dropped"
                        else -> "Unknown"
                    }
                    (holder.categoryColorView as TextView).text = categoryText
                    holder.categoryColorView.setVisibility(View.VISIBLE)
                } else {
                    holder.categoryColorView?.visibility = View.GONE
                }
            }

            // Check if the object has "title" if not,
            // it is a series and "name" is used.
            var dateString =
                if (showData.has(KEY_DATE_MOVIE)) showData.getString(KEY_DATE_MOVIE) else showData.getString(
                    KEY_DATE_SERIES
                )

            // Convert date to locale.
            val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            try {
                val date = originalFormat.parse(dateString)

                if (mGridView == MView.GRID || mGridView == MView.LIST) {
                    val localFormat =
                        DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                    dateString = localFormat.format(date)
                } else {
                    val yearFormat = SimpleDateFormat("yyyy")
                    dateString = yearFormat.format(date)
                }
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            holder.showDate.text = dateString

            // Only if the shows are presented in a list.
            if (mGridView == MView.LIST) {
                holder.showDescription?.text = showData.getString(KEY_DESCRIPTION)

            // Divide the rating in two so it fits in the five stars.
                holder.showRating?.rating = showData.getString(KEY_RATING).toFloat() / 2

                // Remove the [ and ] from the String
                val genreIds = showData.getString(KEY_GENRES)
                    .substring(
                        1, showData.getString(KEY_GENRES)
                            .length - 1
                    )

                // Split the String with ids and set them into an array.
                val genreArray =
                    genreIds.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                // Get sharedPreferences in case the genreHashMap doesn't have the value
                val sharedPreferences = context.getSharedPreferences(
                    "GenreList", Context.MODE_PRIVATE
                )

                // Add all the genres in one String.
                val genreNames = StringBuilder()
                for (aGenreArray in genreArray) {
                    if (mGenreHashMap!![aGenreArray] != null) {
                        genreNames.append(", ").append(mGenreHashMap!![aGenreArray])
                    } else {
                        genreNames.append(", ").append(sharedPreferences.getString(aGenreArray, ""))
                    }
                }

                // Remove the first ", " from the string and set the text.
                holder.showGenre?.text = genreNames.substring(2)
            } else if (mGridView == MView.RECOMMENDATIONS) {
                // Quickly fade in the poster when loaded.
                val animation = AnimationUtils.loadAnimation(
                    context,
                    R.anim.fade_in_fast
                )
                holder.showImage.startAnimation(animation)

                if (showData.has(KEY_RATING)) {
                    // Add rating
                    val voteAverage: Float = showData.getString(KEY_RATING).toFloat()
                    holder.showRatingText?.text = String.format(Locale.getDefault(), "★%.2f", voteAverage)
                }
            } else if (mGridView == MView.ROLES) {
                if (showData.has(KEY_CHARACTER)) {
                    holder.showRole?.text = showData.getString(KEY_CHARACTER)
                }

                if (showData.has(KEY_CREW_JOB)) {
                    holder.showRole?.text = showData.getString(KEY_CREW_JOB)
                }
            } else if (mGridView == MView.FILMOGRAPHY) {
                if (showData.getString("media_type").equals("tv")) {
                    holder.detailButton?.visibility = View.VISIBLE
                } else {
                    holder.detailButton?.visibility = View.GONE
                }

                holder.showRating?.rating = showData.getString(KEY_RATING).toFloat() / 2

                if (showData.has(KEY_CHARACTER)) {
                    holder.showRole?.text = showData.getString(KEY_CHARACTER)
                }

                if (showData.has(KEY_CREW_JOB)) {
                    holder.showRole?.text = showData.getString(KEY_CREW_JOB)
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        // Send the movie data and the user to DetailActivity when clicking on a card.
        holder.itemView.setOnClickListener { view: View ->
            val intent = Intent(view.context, DetailActivity::class.java)
            intent.putExtra("movieObject", showData.toString())
            if (showData.has(KEY_NAME)) {
                intent.putExtra("isMovie", false)
            }
            view.context.startActivity(intent)
        }

        if (mGridView == MView.GRID || mGridView == MView.LIST) {
            if (showDeleteButton) {
                holder.deleteButton?.visibility = View.VISIBLE
                holder.deleteButton?.setOnClickListener {
                    val mediaId: Int
                    val type: String
                    try {
                        mediaId = showData.getInt(KEY_ID)
                        type = if (showData.has(KEY_TITLE)) "movie" else "tv"
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        return@setOnClickListener
                    }
                    val listId =
                        PreferenceManager.getDefaultSharedPreferences(context).getInt("listId", 0)
                    val activity = context as Activity
                    val deleteThread = DeleteFromListThreadTMDb(
                        mediaId,
                        listId,
                        type,
                        activity,
                        position,
                        mShowArrayList,
                        this
                    )
                    CoroutineScope(Dispatchers.Main).launch {
                        deleteThread.deleteFromList()
                    }
                }
            } else {
                holder.deleteButton?.visibility = View.GONE
            }
        }
    }

    override fun getItemId(position: Int): Long {
        // The id is the same as the position,
        // therefore returning the position is enough.
        return position.toLong()
    }


    fun addItems(newItems: ArrayList<JSONObject>) {
        val startPosition = mShowArrayList.size
        mShowArrayList.addAll(newItems)
        notifyItemRangeInserted(startPosition, newItems.size)
    }

    /**
     * The View of every item that is displayed in the grid/list.
     */
    class ShowItemViewHolder internal constructor(itemView: View, gridView: MView) :
        RecyclerView.ViewHolder(itemView) {
        val showView: CardView = itemView.findViewById(R.id.cardView)
        val showTitle: TextView = itemView.findViewById(R.id.title)
        val showImage: ImageView = itemView.findViewById(R.id.image)
        val categoryColorView: View? = itemView.findViewById(R.id.categoryColor)
        val showDate: TextView = itemView.findViewById(R.id.date)
        val deleteButton: Button? = itemView.findViewById(R.id.deleteButton)

        val detailButton: Button? = itemView.findViewById(R.id.detailButton)

        val showDescription: TextView? = if (gridView == MView.LIST) itemView.findViewById(R.id.description) else null
        val showGenre: TextView? = if (gridView == MView.LIST) itemView.findViewById(R.id.genre) else null
        val showRating: RatingBar? = if (gridView == MView.LIST) itemView.findViewById(R.id.rating) else null

        val showRatingText: TextView? = if (gridView == MView.RECOMMENDATIONS) itemView.findViewById(R.id.ratingText) else null
        val showRole: TextView? = if (gridView == MView.ROLES || gridView == MView.FILMOGRAPHY) itemView.findViewById(R.id.role) else null
    }

    companion object {
        // API key names
        const val KEY_ID = "id"
        const val KEY_IMAGE = "backdrop_path"
        const val KEY_POSTER = "poster_path"
        const val KEY_TITLE = "title"
        const val KEY_NAME = "name"
        const val KEY_DESCRIPTION = "overview"
        const val KEY_RATING = "vote_average"
        const val KEY_DATE_MOVIE = "release_date"
        const val KEY_DATE_SERIES = "first_air_date"
        const val KEY_GENRES = "genre_ids"
        const val KEY_RELEASE_DATE = "release_date"
        const val KEY_CHARACTER = "character"
        const val KEY_CREW_JOB = "job"
        private const val HD_IMAGE_SIZE = "key_hq_images"
        private const val KEY_CATEGORIES = MovieDatabaseHelper.COLUMN_CATEGORIES
    }
}
