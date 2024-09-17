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

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.fragment.SeasonDetailsFragment.Companion.newInstance
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

class TVSeasonDetailsActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_season_details)
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
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
        appBarLayout.setBackgroundColor(Color.TRANSPARENT)
        val tvShowId = intent.getIntExtra("tvShowId", -1)
        val seasonNumber = intent.getIntExtra("seasonNumber", 1)
        val numSeasons = intent.getIntExtra("numSeasons", 1)
        val showName = intent.getStringExtra("tvShowName")
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return newInstance(tvShowId, position + 1, showName)
            }

            override fun getItemCount(): Int {
                return numSeasons
            }
        }
        viewPager.setCurrentItem(seasonNumber - 1, false)
        TabLayoutMediator(
            tabLayout, viewPager
        ) { tab: TabLayout.Tab, position: Int -> tab.setText("Season " + (position + 1)) }.attach()
    }
}