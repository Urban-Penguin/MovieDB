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
package com.wirelessalien.android.moviedb.helper

import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.data.EpisodeDbDetails
import com.wirelessalien.android.moviedb.helper.DirectoryHelper.getExportDirectory
import com.wirelessalien.android.moviedb.listener.AdapterDataChangedListener
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CompletableFuture

/**
 * This class provides some (basic) database functionality.
 */
class MovieDatabaseHelper  // Initialize the database object.
/**
 * Initialises the database object.
 *
 * @param context the context passed on to the super.
 */
    (context: Context?) : SQLiteOpenHelper(context, databaseFileName, null, DATABASE_VERSION) {
    /**
     * Converts the show table in the database to a JSON string.
     *
     * @param database the database to get the data from.
     * @return a string in JSON format containing all the show data.
     */
    private fun getEpisodesForMovie(movieId: Int, database: SQLiteDatabase): JSONArray {
        val selectQuery =
            "SELECT * FROM $TABLE_EPISODES WHERE $COLUMN_MOVIES_ID = $movieId"
        val cursor = database.rawQuery(selectQuery, null)

        // Convert the database to JSON
        val episodesSet = JSONArray()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val totalColumn = cursor.columnCount
            val rowObject = JSONObject()
            for (i in 0 until totalColumn) {
                if (cursor.getColumnName(i) != null) {
                    try {
                        if (cursor.getString(i) != null) {
                            rowObject.put(cursor.getColumnName(i), cursor.getString(i))
                        } else {
                            rowObject.put(cursor.getColumnName(i), "")
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
            episodesSet.put(rowObject)
            cursor.moveToNext()
        }
        cursor.close()
        return episodesSet
    }

    private fun getJSONExportString(database: SQLiteDatabase): String {
        val selectQuery = "SELECT * FROM $TABLE_MOVIES"
        val cursor = database.rawQuery(selectQuery, null)

        // Convert the database to JSON
        val databaseSet = JSONArray()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val totalColumn = cursor.columnCount
            val rowObject = JSONObject()
            for (i in 0 until totalColumn) {
                if (cursor.getColumnName(i) != null) {
                    try {
                        if (cursor.getString(i) != null) {
                            rowObject.put(cursor.getColumnName(i), cursor.getString(i))
                        } else {
                            rowObject.put(cursor.getColumnName(i), "")
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }

            // Add episodes for this movie
            val movieId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MOVIES_ID))
            try {
                rowObject.put("episodes", getEpisodesForMovie(movieId, database))
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
            databaseSet.put(rowObject)
            cursor.moveToNext()
        }
        cursor.close()

        // Convert databaseSet to string and put in file
        return databaseSet.toString()
    }

    /**
     * Writes the database in the chosen format to the downloads directory.
     *
     * @param context the context needed for the dialogs and toasts.
     */
    fun exportDatabase(context: Context) {
        val builder = MaterialAlertDialogBuilder(context)

        // Inflate the custom layout
        val inflater = LayoutInflater.from(context)
        val customView = inflater.inflate(R.layout.export_dialog, null)
        val jsonRadioButton = customView.findViewById<RadioButton>(R.id.radio_json)
        val dbRadioButton = customView.findViewById<RadioButton>(R.id.radio_db)
        builder.setView(customView) // Set the custom layout
        builder.setTitle(context.resources.getString(R.string.choose_export_file))
            .setPositiveButton("Export") { dialogInterface: DialogInterface?, i: Int ->
                val exportDirectory = getExportDirectory(context)
                if (exportDirectory != null) {
                    val data = Environment.getDataDirectory()
                    val currentDBPath =
                        "/data/" + context.packageName + "/databases/" + databaseFileName
                    val simpleDateFormat = SimpleDateFormat("dd-MM-yy-kk-mm", Locale.US)
                    if (jsonRadioButton.isChecked) {
                        // Convert databaseSet to string and put in file
                        val fileContent = getJSONExportString(readableDatabase)
                        val fileExtension = ".json"

                        // Write to file
                        val fileName =
                            DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                        try {
                            val file = File(exportDirectory, fileName)
                            val bufferedOutputStream = BufferedOutputStream(FileOutputStream(file))
                            bufferedOutputStream.write(fileContent.toByteArray())
                            bufferedOutputStream.flush()
                            bufferedOutputStream.close()
                            Toast.makeText(
                                context,
                                context.resources.getString(R.string.write_to_external_storage_as) + fileName,
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    } else if (dbRadioButton.isChecked) {
                        // Write the .db file to selected directory
                        val fileExtension = ".db"
                        val exportDBPath =
                            DATABASE_FILE_NAME + simpleDateFormat.format(Date()) + fileExtension
                        try {
                            val currentDB = File(data, currentDBPath)
                            val exportDB = File(exportDirectory, exportDBPath)
                            val bufferedOutputStream =
                                BufferedOutputStream(FileOutputStream(exportDB))
                            val fileChannel = FileInputStream(currentDB).channel
                            val buffer = ByteBuffer.allocate(fileChannel.size().toInt())
                            fileChannel.read(buffer)
                            buffer.flip()
                            val byteArray = ByteArray(buffer.remaining())
                            buffer[byteArray]
                            bufferedOutputStream.write(byteArray)
                            bufferedOutputStream.flush()
                            bufferedOutputStream.close()
                            Toast.makeText(
                                context,
                                context.resources.getString(R.string.write_to_external_storage_as) + exportDBPath,
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    // Show error message if no directory is selected
                    Toast.makeText(context, "Failed to export the database", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("Cancel") { dialogInterface: DialogInterface, i: Int -> dialogInterface.cancel() }
        builder.show()
    }

    /**
     * Displays a dialog with possible files to import and imports the chosen file.
     * The current database will be dropped in that case.
     *
     * @param context the context needed for the dialog.
     */
    fun importDatabase(context: Context, listener: AdapterDataChangedListener) {
        // Ask the user which file to import
        val downloadPath = context.cacheDir.path
        val directory = File(downloadPath)
        val files = directory.listFiles { pathname: File ->
            // Only show database
            val name = pathname.name
            name.endsWith(".db")
        }
        val fileAdapter = ArrayAdapter<String>(context, android.R.layout.select_dialog_singlechoice)
        for (file in files) {
            fileAdapter.add(file.name)
        }
        val fileDialog = MaterialAlertDialogBuilder(context)
        fileDialog.setTitle(R.string.choose_file)
        fileDialog.setNegativeButton(R.string.import_cancel) { dialog: DialogInterface, which: Int -> dialog.dismiss() }

        // Show the files that can be imported.
        fileDialog.setAdapter(fileAdapter) { dialog: DialogInterface?, which: Int ->
            val path = File(context.cacheDir.path)
            try {
                val exportDBPath = fileAdapter.getItem(which)
                if (exportDBPath == null) {
                    throw NullPointerException()
                } else if (fileAdapter.getItem(which)!!.endsWith(".db")) {
                    CompletableFuture.runAsync {
                        try {
                            // Import the file selected in the dialog.
                            val data = Environment.getDataDirectory()
                            val currentDBPath =
                                "/data/" + context.packageName + "/databases/" + databaseFileName
                            val currentDB = File(data, currentDBPath)
                            val importDB = File(path, exportDBPath)
                            val src = FileInputStream(importDB).channel
                            val dst = FileOutputStream(currentDB).channel
                            dst.transferFrom(src, 0, src.size())
                            src.close()
                            dst.close()
                            val mainHandler = Handler(Looper.getMainLooper())
                            mainHandler.post {
                                Toast.makeText(
                                    context,
                                    R.string.database_import_successful,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (npe: NullPointerException) {
                npe.printStackTrace()
                Toast.makeText(
                    context,
                    context.resources.getString(R.string.file_not_found_exception),
                    Toast.LENGTH_SHORT
                ).show()
            }
            listener.onAdapterDataChangedListener()
        }
        fileDialog.show()
    }

    override fun onCreate(database: SQLiteDatabase) {
        // Create the database with the database creation statement.
        val DATABASE_CREATE = ("CREATE TABLE IF NOT EXISTS " +
                TABLE_MOVIES + "(" + COLUMN_ID + " integer primary key autoincrement, " +
                COLUMN_MOVIES_ID + " integer not null, " + COLUMN_RATING +
                " REAL not null, " + COLUMN_PERSONAL_RATING + " REAL, " +
                COLUMN_IMAGE + " text not null, " + COLUMN_ICON + " text not null, " +
                COLUMN_TITLE + " text not null, " + COLUMN_SUMMARY + " text not null, " +
                COLUMN_GENRES + " text not null, " + COLUMN_GENRES_IDS
                + " text not null, " + COLUMN_RELEASE_DATE + " text, " + COLUMN_PERSONAL_START_DATE + " text, " +
                COLUMN_PERSONAL_FINISH_DATE + " text, " + COLUMN_PERSONAL_REWATCHED +
                " integer, " + COLUMN_CATEGORIES + " integer not null, " + COLUMN_MOVIE +
                " integer not null, " + COLUMN_PERSONAL_EPISODES + " integer, " +
                COLUMN_MOVIE_REVIEW + " TEXT);")
        database.execSQL(DATABASE_CREATE)
        val CREATE_EPISODES_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_EPISODES + "(" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_MOVIES_ID + " INTEGER NOT NULL, " +
                COLUMN_SEASON_NUMBER + " INTEGER NOT NULL, " +
                COLUMN_EPISODE_NUMBER + " INTEGER NOT NULL, " +
                COLUMN_EPISODE_RATING + " REAL, " +
                COLUMN_EPISODE_WATCH_DATE + " TEXT, " +
                COLUMN_EPISODE_REVIEW + " TEXT);"
        database.execSQL(CREATE_EPISODES_TABLE)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(
            MovieDatabaseHelper::class.java.name, "Upgrading database from version "
                    + oldVersion + " to " + newVersion +
                    ", database will be temporarily exported to a JSON string and imported after the upgrade."
        )
        if (oldVersion < 12) {
            val CREATE_EPISODES_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_EPISODES + "(" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_MOVIES_ID + " INTEGER NOT NULL, " +
                    COLUMN_SEASON_NUMBER + " INTEGER NOT NULL, " +
                    COLUMN_EPISODE_NUMBER + " INTEGER NOT NULL);"
            database.execSQL(CREATE_EPISODES_TABLE)
        }
        if (oldVersion < 13) {
            var ALTER_EPISODES_TABLE =
                "ALTER TABLE $TABLE_EPISODES ADD COLUMN $COLUMN_EPISODE_RATING REAL;"
            database.execSQL(ALTER_EPISODES_TABLE)
            ALTER_EPISODES_TABLE =
                "ALTER TABLE $TABLE_EPISODES ADD COLUMN $COLUMN_EPISODE_WATCH_DATE TEXT;"
            database.execSQL(ALTER_EPISODES_TABLE)
        }
        if (oldVersion < 14) {
            // Add the movie_review column to the old table if it doesn't exist
            if (isColumnExists(database, TABLE_MOVIES, COLUMN_MOVIE_REVIEW)) {
                val ADD_MOVIE_REVIEW_COLUMN =
                    "ALTER TABLE $TABLE_MOVIES ADD COLUMN $COLUMN_MOVIE_REVIEW TEXT;"
                database.execSQL(ADD_MOVIE_REVIEW_COLUMN)
            }

            // Create a new table with the desired schema
            val CREATE_NEW_MOVIES_TABLE = "CREATE TABLE movies_new (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_MOVIES_ID + " INTEGER NOT NULL, " +
                    COLUMN_RATING + " REAL NOT NULL, " +
                    COLUMN_PERSONAL_RATING + " REAL, " +
                    COLUMN_IMAGE + " TEXT NOT NULL, " +
                    COLUMN_ICON + " TEXT NOT NULL, " +
                    COLUMN_TITLE + " TEXT NOT NULL, " +
                    COLUMN_SUMMARY + " TEXT NOT NULL, " +
                    COLUMN_GENRES + " TEXT NOT NULL, " +
                    COLUMN_GENRES_IDS + " TEXT NOT NULL, " +
                    COLUMN_RELEASE_DATE + " TEXT, " +
                    COLUMN_PERSONAL_START_DATE + " TEXT, " +
                    COLUMN_PERSONAL_FINISH_DATE + " TEXT, " +
                    COLUMN_PERSONAL_REWATCHED + " INTEGER, " +
                    COLUMN_CATEGORIES + " INTEGER NOT NULL, " +
                    COLUMN_MOVIE + " INTEGER NOT NULL, " +
                    COLUMN_PERSONAL_EPISODES + " INTEGER, " +
                    COLUMN_MOVIE_REVIEW + " TEXT);"
            database.execSQL(CREATE_NEW_MOVIES_TABLE)

            // Copy data from the old table to the new table
            val COPY_DATA = "INSERT INTO movies_new (" +
                    COLUMN_ID + ", " +
                    COLUMN_MOVIES_ID + ", " +
                    COLUMN_RATING + ", " +
                    COLUMN_PERSONAL_RATING + ", " +
                    COLUMN_IMAGE + ", " +
                    COLUMN_ICON + ", " +
                    COLUMN_TITLE + ", " +
                    COLUMN_SUMMARY + ", " +
                    COLUMN_GENRES + ", " +
                    COLUMN_GENRES_IDS + ", " +
                    COLUMN_RELEASE_DATE + ", " +
                    COLUMN_PERSONAL_START_DATE + ", " +
                    COLUMN_PERSONAL_FINISH_DATE + ", " +
                    COLUMN_PERSONAL_REWATCHED + ", " +
                    COLUMN_CATEGORIES + ", " +
                    COLUMN_MOVIE + ", " +
                    COLUMN_PERSONAL_EPISODES + ", " +
                    COLUMN_MOVIE_REVIEW + ") " +
                    "SELECT " +
                    COLUMN_ID + ", " +
                    COLUMN_MOVIES_ID + ", " +
                    COLUMN_RATING + ", " +
                    COLUMN_PERSONAL_RATING + ", " +
                    COLUMN_IMAGE + ", " +
                    COLUMN_ICON + ", " +
                    COLUMN_TITLE + ", " +
                    COLUMN_SUMMARY + ", " +
                    COLUMN_GENRES + ", " +
                    COLUMN_GENRES_IDS + ", " +
                    COLUMN_RELEASE_DATE + ", " +
                    COLUMN_PERSONAL_START_DATE + ", " +
                    COLUMN_PERSONAL_FINISH_DATE + ", " +
                    COLUMN_PERSONAL_REWATCHED + ", " +
                    COLUMN_CATEGORIES + ", " +
                    COLUMN_MOVIE + ", " +
                    COLUMN_PERSONAL_EPISODES + ", " +
                    COLUMN_MOVIE_REVIEW + " FROM " + TABLE_MOVIES + ";"
            database.execSQL(COPY_DATA)

            // Drop the old table
            val DROP_OLD_TABLE = "DROP TABLE $TABLE_MOVIES;"
            database.execSQL(DROP_OLD_TABLE)

            // Rename the new table to the old table's name
            val RENAME_TABLE = "ALTER TABLE movies_new RENAME TO $TABLE_MOVIES;"
            database.execSQL(RENAME_TABLE)
        }
        if (oldVersion < 15) {
            if (isColumnExists(database, TABLE_MOVIES, COLUMN_MOVIE_REVIEW)) {
                val ALTER_MOVIES_TABLE =
                    "ALTER TABLE $TABLE_MOVIES ADD COLUMN $COLUMN_MOVIE_REVIEW TEXT;"
                database.execSQL(ALTER_MOVIES_TABLE)
            }
            if (isColumnExists(database, TABLE_EPISODES, COLUMN_EPISODE_REVIEW)) {
                val ALTER_EPISODES_TABLE =
                    "ALTER TABLE $TABLE_EPISODES ADD COLUMN $COLUMN_EPISODE_REVIEW TEXT;"
                database.execSQL(ALTER_EPISODES_TABLE)
            }
        }
        onCreate(database)
    }

    private fun isColumnExists(
        database: SQLiteDatabase,
        tableName: String,
        columnName: String
    ): Boolean {
        val cursor = database.rawQuery("PRAGMA table_info($tableName)", null)
        val columnIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(columnIndex) == columnName) {
                cursor.close()
                return false
            }
        }
        cursor.close()
        return true
    }

    fun addOrUpdateEpisode(
        movieId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        rating: Float,
        watchDate: String?,
        review: String?
    ) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_MOVIES_ID, movieId)
        values.put(COLUMN_SEASON_NUMBER, seasonNumber)
        values.put(COLUMN_EPISODE_NUMBER, episodeNumber)
        // Only add rating and watch date if they are not null
        if (rating.toDouble() != 0.0) {
            values.put(COLUMN_EPISODE_RATING, rating)
        }
        if (watchDate != null) {
            values.put(COLUMN_EPISODE_WATCH_DATE, watchDate)
        }
        if (review != null) {
            values.put(COLUMN_EPISODE_REVIEW, review)
        }

        // Check if the episode already exists
        val exists = isEpisodeInDatabase(movieId, seasonNumber, listOf(episodeNumber))
        if (exists) {
            // Update existing episode
            val whereClause =
                "$COLUMN_MOVIES_ID=? AND $COLUMN_SEASON_NUMBER=? AND $COLUMN_EPISODE_NUMBER=?"
            val whereArgs =
                arrayOf(movieId.toString(), seasonNumber.toString(), episodeNumber.toString())
            db.update(TABLE_EPISODES, values, whereClause, whereArgs)
        } else {
            // Insert new episode
            db.insert(TABLE_EPISODES, null, values)
        }
    }

    fun getEpisodeDetails(movieId: Int, seasonNumber: Int, episodeNumber: Int): EpisodeDbDetails? {
        val db = this.readableDatabase
        val columns =
            arrayOf(COLUMN_EPISODE_RATING, COLUMN_EPISODE_WATCH_DATE, COLUMN_EPISODE_REVIEW)
        val selection =
            "$COLUMN_MOVIES_ID = ? AND $COLUMN_SEASON_NUMBER = ? AND $COLUMN_EPISODE_NUMBER = ?"
        val selectionArgs =
            arrayOf(movieId.toString(), seasonNumber.toString(), episodeNumber.toString())
        val cursor = db.query(TABLE_EPISODES, columns, selection, selectionArgs, null, null, null)
        var details: EpisodeDbDetails? = null
        if (cursor.moveToFirst()) {
            val rating = if (cursor.isNull(0)) null else cursor.getFloat(0)
            val watchDate = cursor.getString(1)
            val review = cursor.getString(2)
            details = EpisodeDbDetails(rating, watchDate, review)
        }
        cursor.close()
        return details
    }

    fun addEpisodeNumber(movieId: Int, seasonNumber: Int, episodeNumbers: List<Int?>) {
        val db = this.writableDatabase
        val values = ContentValues()
        for (episodeNumber in episodeNumbers) {
            values.put(COLUMN_MOVIES_ID, movieId)
            values.put(COLUMN_SEASON_NUMBER, seasonNumber)
            values.put(COLUMN_EPISODE_NUMBER, episodeNumber)
            db.insert(TABLE_EPISODES, null, values)
        }
    }

    fun removeEpisodeNumber(movieId: Int, seasonNumber: Int, episodeNumbers: List<Int>) {
        val db = this.writableDatabase
        for (episodeNumber in episodeNumbers) {
            db.delete(
                TABLE_EPISODES,
                "$COLUMN_MOVIES_ID = ? AND $COLUMN_SEASON_NUMBER = ? AND $COLUMN_EPISODE_NUMBER = ?",
                arrayOf(movieId.toString(), seasonNumber.toString(), episodeNumber.toString())
            )
        }
    }

    fun isEpisodeInDatabase(movieId: Int, seasonNumber: Int, episodeNumbers: List<Int>): Boolean {
        val db = this.readableDatabase
        val selection =
            "$COLUMN_MOVIES_ID = ? AND $COLUMN_SEASON_NUMBER = ? AND $COLUMN_EPISODE_NUMBER = ?"
        var exists = false
        for (episodeNumber in episodeNumbers) {
            val cursor = db.query(
                TABLE_EPISODES,
                null,
                selection,
                arrayOf(movieId.toString(), seasonNumber.toString(), episodeNumber.toString()),
                null,
                null,
                null
            )
            if (cursor.count > 0) {
                exists = true
            }
            cursor.close()
        }
        return exists
    }

    fun getSeenEpisodesCount(movieId: Int): Int {
        val db = this.readableDatabase
        val countQuery =
            "SELECT * FROM $TABLE_EPISODES WHERE $COLUMN_MOVIES_ID = $movieId"
        val cursor = db.rawQuery(countQuery, null)
        val count = cursor.count
        cursor.close()
        return count
    }

    companion object {
        const val TABLE_MOVIES = "movies"
        const val TABLE_EPISODES = "episodes"
        const val COLUMN_ID = "id"
        const val COLUMN_MOVIES_ID = "movie_id"
        const val COLUMN_RATING = "rating"
        const val COLUMN_PERSONAL_RATING = "personal_rating"
        const val COLUMN_IMAGE = "image"
        const val COLUMN_ICON = "icon"
        const val COLUMN_TITLE = "title"
        const val COLUMN_SUMMARY = "summary"
        const val COLUMN_GENRES = "genres"
        const val COLUMN_GENRES_IDS = "genres_ids"
        const val COLUMN_RELEASE_DATE = "release_date"
        const val COLUMN_PERSONAL_START_DATE = "personal_start_date"
        const val COLUMN_PERSONAL_FINISH_DATE = "personal_finish_date"
        const val COLUMN_PERSONAL_REWATCHED = "personal_rewatched"
        const val COLUMN_PERSONAL_EPISODES = "personal_episodes"
        const val COLUMN_EPISODE_NUMBER = "episode_number"
        const val COLUMN_SEASON_NUMBER = "season_number"
        const val COLUMN_EPISODE_WATCH_DATE = "episode_watch_date"
        const val COLUMN_EPISODE_RATING = "episode_rating"
        const val COLUMN_CATEGORIES = "watched"
        const val COLUMN_MOVIE = "movie"
        const val COLUMN_MOVIE_REVIEW = "movie_review"
        const val COLUMN_EPISODE_REVIEW = "episode_review"
        const val CATEGORY_WATCHING = 2
        const val CATEGORY_PLAN_TO_WATCH = 0
        const val CATEGORY_WATCHED = 1
        const val CATEGORY_ON_HOLD = 3
        const val CATEGORY_DROPPED = 4

        /**
         * Returns the database name.
         *
         * @return the database name.
         */
        const val databaseFileName = "movies.db"
        private const val DATABASE_FILE_NAME = "movies"
        private const val DATABASE_FILE_EXT = ".db"
        private const val DATABASE_VERSION = 15
    }
}
