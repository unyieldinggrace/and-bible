/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.view.activity.page.screen

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Build

import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.Window.WindowOperation
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.BibleViewFactory
import net.bible.android.view.activity.page.MainBibleActivity
import java.util.*

import javax.inject.Inject


/**
 * TEMP NOTE: http://stackoverflow.com/questions/961944/overlapping-views-in-android
 * FOR OVERLAY IMAGE
 */
/**
 * Build the main WebView component for displaying most document types
 *
 * Structure of the layout:
 * parent
 * windowFrameLayout
 * bibleView
 * separatorExtension (touch delegate for next separator)
 * separator
 * windowFrameLayout
 * bibleView
 * separatorExtension (touch delegate for previous separator)
 * minimiseButton
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class DocumentWebViewBuilder @Inject constructor(
        private val windowControl: WindowControl,
        private val mainBibleActivity: MainBibleActivity,
        private val bibleViewFactory: BibleViewFactory,
        private val windowMenuCommandHandler: WindowMenuCommandHandler
) {

    private var isWindowConfigurationChanged = true

    private var isLaidOutWithHorizontalSplit: Boolean = false

    private val WINDOW_SEPARATOR_WIDTH_PX: Int
    private val WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX: Int
    private val WINDOW_BUTTON_TEXT_COLOUR: Int
    private val WINDOW_BUTTON_BACKGROUND_COLOUR: Int
    private val BUTTON_SIZE_PX: Int

    private var previousParent: LinearLayout? = null

    init {

        val res = BibleApplication.application.resources
        WINDOW_SEPARATOR_WIDTH_PX = res.getDimensionPixelSize(R.dimen.window_separator_width)
        WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX = res.getDimensionPixelSize(R.dimen.window_separator_touch_expansion_width)
        WINDOW_BUTTON_TEXT_COLOUR = res.getColor(R.color.window_button_text_colour)
        WINDOW_BUTTON_BACKGROUND_COLOUR = res.getColor(R.color.window_button_background_colour)

        BUTTON_SIZE_PX = res.getDimensionPixelSize(R.dimen.minimise_restore_button_size)

        // Be notified of any changes to window config
        ABEventBus.getDefault().register(this)
    }

    /**
     * Record changes to scplit screen config so can redraw screen from scratch.
     */
    fun onEvent(event: NumberOfWindowsChangedEvent) {
        isWindowConfigurationChanged = true
    }

    /**
     * Enable switch from Bible WebView to MyNote view
     */
    fun removeWebView(parent: ViewGroup) {
        val isWebView = isWebViewShowing(parent)

        if (isWebView) {
            parent.tag = ""
            removeChildViews(parent)
        }
    }

    @SuppressLint("RtlHardcoded")
    fun addWebView(parent: LinearLayout) {
        val isWebView = isWebViewShowing(parent)
        parent.tag = TAG
        val isSplitHorizontally = mainBibleActivity.isSplitHorizontally

        if (!isWebView ||
                isWindowConfigurationChanged ||
                isSplitHorizontally != isLaidOutWithHorizontalSplit) {
            Log.d(TAG, "Layout web view")

            val windows = windowControl.windowRepository.visibleWindows

            // ensure we have a known starting point - could be none, 1, or 2 webviews present
            removeChildViews(previousParent)

            parent.orientation = if (isSplitHorizontally) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            var currentWindowFrameLayout: ViewGroup? = null
            var previousSeparator: Separator? = null
            windowButtons.clear()
            for ((windowNo, window) in windows.withIndex()) {
                Log.d(TAG, "Layout screen " + window.screenNo + " of " + windows.size)

                currentWindowFrameLayout = FrameLayout(this.mainBibleActivity)

                val bibleView = getCleanView(window)

                // trigger recalc of verse positions in case width changes e.g. minimize/restore web view
                bibleView.setVersePositionRecalcRequired(true)

                val windowWeight = window.windowLayout.weight
                val lp = if (isSplitHorizontally)
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, windowWeight)
                else
                    LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, windowWeight)

                parent.addView(currentWindowFrameLayout, lp)

                // add bible to framelayout
                val frameLayoutParamsBibleWebView = FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                currentWindowFrameLayout.addView(bibleView, frameLayoutParamsBibleWebView)

                if (windowNo > 0) {
                    val separator = previousSeparator

                    // extend touch area of separator
                    addTopOrLeftSeparatorExtension(isSplitHorizontally, currentWindowFrameLayout, lp, separator!!)
                }

                // Add screen separator
                if (windowNo < windows.size - 1) {
                    val nextWindow = windows[windowNo + 1]
                    val separator = createSeparator(parent, window, nextWindow, isSplitHorizontally, windows.size)

                    // extend touch area of separator
                    addBottomOrRightSeparatorExtension(isSplitHorizontally, currentWindowFrameLayout, lp, separator)

                    // Add actual separator line dividing two windows
                    parent.addView(separator, if (isSplitHorizontally)
                        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, WINDOW_SEPARATOR_WIDTH_PX, 0f)
                    else
                        LinearLayout.LayoutParams(WINDOW_SEPARATOR_WIDTH_PX, LayoutParams.MATCH_PARENT, 0f))
                    // allow extension to be added in next screen
                    previousSeparator = separator
                }

                // create default action button for top right of each window
                val defaultWindowActionButton = createDefaultWindowActionButton(window)

                if(!isSplitHorizontally) {
                    defaultWindowActionButton.translationY = mainBibleActivity.topOffset2
                    if(windowNo == windows.size - 1) {
                        defaultWindowActionButton.translationX = -mainBibleActivity.rightOffset1
                    }
                }
                else {
                    if(windowNo == 0) {
                        defaultWindowActionButton.translationY = mainBibleActivity.topOffset2
                    }
                    defaultWindowActionButton.translationX = -mainBibleActivity.rightOffset1
                }

                windowButtons.add(defaultWindowActionButton)
                currentWindowFrameLayout.addView(defaultWindowActionButton,
                        FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.TOP or Gravity.RIGHT))
                window.bibleView = bibleView

            }

            // Display minimised screens
            restoreButtons.clear()
            minimisedWindowsFrameContainer = LinearLayout(mainBibleActivity)
            currentWindowFrameLayout!!.addView(minimisedWindowsFrameContainer,
                    FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, BUTTON_SIZE_PX,
                            Gravity.BOTTOM or Gravity.RIGHT))
            minimisedWindowsFrameContainer.translationY = -mainBibleActivity.bottomOffset2
            minimisedWindowsFrameContainer.translationX = -mainBibleActivity.rightOffset1
            val minimisedScreens = windowControl.windowRepository.minimisedScreens
            for (i in minimisedScreens.indices) {
                Log.d(TAG, "Show restore button")
                val restoreButton = createRestoreButton(minimisedScreens[i])
                restoreButtons.add(restoreButton)
                minimisedWindowsFrameContainer.addView(restoreButton,
                        LinearLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX))
            }

            previousParent = parent
            isLaidOutWithHorizontalSplit = isSplitHorizontally
            isWindowConfigurationChanged = false
        }
        resetTouchTimer()
        mainBibleActivity.resetSystemUi()
    }

    private val windowButtons: MutableList<Button> = ArrayList()
    private val restoreButtons: MutableList<Button> = ArrayList()
    private lateinit var minimisedWindowsFrameContainer: LinearLayout

    fun onEvent(event: MainBibleActivity.FullScreenEvent) {
        toggleWindowButtonVisibility(true, force=true)
        resetTouchTimer()
    }

    fun onEvent(event: MainBibleActivity.ConfigurationChanged) {
        toggleWindowButtonVisibility(true, force=true)
        resetTouchTimer()
    }

    fun onEvent(event: MainBibleActivity.TransportBarVisibilityChanged) {
        toggleWindowButtonVisibility(true, force=true)
        resetTouchTimer()
    }

    private var sleepTimer: Timer = Timer("TTS sleep timer")
    private var timerTask: TimerTask? = null

    fun onEvent(event: BibleView.BibleViewTouched) {
        resetTouchTimer()
    }

    private fun resetTouchTimer() {
        toggleWindowButtonVisibility(true)
        timerTask?.cancel()
        timerTask = object : TimerTask() {
            override fun run() {
                toggleWindowButtonVisibility(false)
            }
        }
        sleepTimer.schedule(timerTask, 3000L)
    }

    private var buttonsVisible = true
    private fun toggleWindowButtonVisibility(show: Boolean, force: Boolean = false) {
        if(buttonsVisible == show && !force) {
            return
        }
        mainBibleActivity.runOnUiThread {
            for ((idx, b) in windowButtons.withIndex()) {
                if(mainBibleActivity.isSplitHorizontally) {
                    b.animate().translationY(if(idx == 0) mainBibleActivity.topOffset2 else 0.0F)
                            .setInterpolator(DecelerateInterpolator()).start()

                    if(show) {
                        b.visibility = View.VISIBLE
                        b.animate().translationX(-mainBibleActivity.rightOffset1)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                    }  else {
                        b.animate().translationX(b.width.toFloat())
                                .setInterpolator(AccelerateInterpolator())
                                .withEndAction { b.visibility = View.GONE }
                                .start()
                    }
                }
                else {
                    b.animate().translationX(-mainBibleActivity.rightOffset1)
                            .setInterpolator(DecelerateInterpolator()).start()
                    if(show) {
                        b.visibility = View.VISIBLE
                        b.animate().translationY(mainBibleActivity.topOffset2)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                    }  else {
                        b.animate().translationY(-b.height.toFloat())
                                .setInterpolator(AccelerateInterpolator())
                                .withEndAction { b.visibility = View.GONE }
                                .start()
                    }
                }
            }
            updateMinimizedButtons(show)
        }
        buttonsVisible = show
    }

    fun updateMinimizedButtons(show: Boolean) {
        if(show) {
            minimisedWindowsFrameContainer.visibility = View.VISIBLE
            minimisedWindowsFrameContainer.animate().translationY(-mainBibleActivity.bottomOffset2)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
        }  else {
            minimisedWindowsFrameContainer.animate().translationY(mainBibleActivity.bottomOffset2 + minimisedWindowsFrameContainer.height)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { minimisedWindowsFrameContainer.visibility = View.GONE }
                    .start()
        }
    }

    private fun createDefaultWindowActionButton(window: Window): Button {
        val b = when {
            window.defaultOperation == WindowOperation.CLOSE -> // close button for the links window
                createCloseButton(window)
            window.defaultOperation == WindowOperation.MAXIMISE -> // normalise button for maximised window
                createMaximiseToggleButton(window)
            else -> // minimise button for normal window
                createMinimiseButton(window)
        }
        return b
    }

    /**
     * Add extension preceding separator
     */
    @SuppressLint("RtlHardcoded")
    private fun addBottomOrRightSeparatorExtension(isPortrait: Boolean,
                                                   previousWindowLayout: ViewGroup,
                                                   previousLp: LinearLayout.LayoutParams,
                                                   separator: Separator) {
        // add first touch delegate to framelayout which extends the touch area, otherwise it is difficult to select the separator to move it
        val frameLayoutParamsSeparatorDelegate = if (isPortrait)
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.BOTTOM)
        else
            FrameLayout.LayoutParams(WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.MATCH_PARENT, Gravity.RIGHT)
        previousWindowLayout.addView(separator.touchDelegateView1, frameLayoutParamsSeparatorDelegate)
        // separator will adjust layouts when dragged
        separator.setView1LayoutParams(previousLp)
    }

    /**
     * Add extension after separator
     */
    @SuppressLint("RtlHardcoded")
    private fun addTopOrLeftSeparatorExtension(isPortrait: Boolean,
                                               currentWindowLayout: ViewGroup,
                                               lp: LinearLayout.LayoutParams,
                                               separator: Separator) {
        // add separator handle touch delegate to framelayout
        val frameLayoutParamsSeparatorDelegate = if (isPortrait)
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.TOP)
        else
            FrameLayout.LayoutParams(WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.MATCH_PARENT, Gravity.LEFT)
        currentWindowLayout.addView(separator.touchDelegateView2, frameLayoutParamsSeparatorDelegate)

        // separator will adjust layouts when dragged
        separator.setView2LayoutParams(lp)
    }

    private fun createSeparator(
            parent: LinearLayout,
            window: Window,
            nextScreen: Window,
            isPortrait: Boolean,
            numWindows: Int): Separator {
        return Separator(this.mainBibleActivity, WINDOW_SEPARATOR_WIDTH_PX, parent, window, nextScreen,
                numWindows, isPortrait, windowControl)
    }

    /**
     * parent contains Frame, seperator, Frame.
     * Frame contains BibleView
     * @param parent
     */
    private fun removeChildViews(parent: ViewGroup?) {
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val view = parent.getChildAt(i)
                if (view is ViewGroup) {
                    // this detaches the BibleView from it's containing Frame
                    removeChildViews(view)
                }
            }

            parent.removeAllViews()
        }
    }

    /**
     * Attempt to fix occasional error: "The specified child already has a parent. You must call removeView() on the child's parent first."
     */
    private fun getCleanView(window: Window): BibleView {
        val bibleView = getView(window)
        val parent = bibleView.parent
        if (parent != null && parent is ViewGroup) {
            parent.removeView(bibleView)
        }
        return bibleView
    }

    fun getView(window: Window): BibleView {
        return bibleViewFactory.createBibleView(window)
    }

    private fun createCloseButton(window: Window): Button {
        return createTextButton("X",
                { v -> showPopupWindow(window, v) },
                { v -> windowControl.closeWindow(window); true}
        )
    }

    private fun createMaximiseToggleButton(window: Window): Button {
        val text = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "⇕" else "━━"
        val b = createTextButton(text,
                { v -> showPopupWindow(window, v) },
                { v -> windowControl.unmaximiseWindow(window); true}
        )
        b.translationY = mainBibleActivity.topOffset2
        b.translationX = -mainBibleActivity.rightOffset1
        return b
    }

    private fun createMinimiseButton(window: Window): Button {
        val text = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "☰" else "━━"
        return createTextButton(text,
                { v -> showPopupWindow(window, v) },
                { v -> windowControl.minimiseWindow(window); true}
        )
    }

    private fun createRestoreButton(window: Window): Button {
        return createTextButton(getDocumentInitial(window),
                { windowControl.restoreWindow(window) },
                { windowControl.restoreWindow(window); true }
        )
    }

    /**
     * Get the first initial of the doc in the window to show in the minimise restore button
     */
    private fun getDocumentInitial(window: Window): String {
        return try {
            window.pageManager.currentPage.currentDocument.abbreviation.substring(0, 1)
        } catch (e: Exception) {
            " "
        }

    }

    private fun createTextButton(text: String, onClickListener: (View) -> Unit,
                                 onLongClickListener: ((View) -> Boolean)? = null): Button {
        val b = Button(mainBibleActivity).apply {
            this.text = text
            width = BUTTON_SIZE_PX
            height = BUTTON_SIZE_PX
            setTextColor(WINDOW_BUTTON_TEXT_COLOUR)
            setTypeface(null, Typeface.BOLD)
            textSize = 20.0F
            setSingleLine(true)
            setOnClickListener(onClickListener)
            setOnLongClickListener(onLongClickListener)
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                setBackgroundResource(R.drawable.window_button)
            }
        }
        return b
    }

    private fun createImageButton(drawableId: Int, onClickListener: (View) -> Unit, onLongClickListener: ((View) -> Boolean)? = null) =
            Button(mainBibleActivity).apply {
                setBackgroundColor(WINDOW_BUTTON_BACKGROUND_COLOUR)
                setBackgroundResource(drawableId)
                width = BUTTON_SIZE_PX
                height = BUTTON_SIZE_PX
                setOnClickListener(onClickListener)
                setOnLongClickListener(onLongClickListener)
            }

    @SuppressLint("RestrictedApi")
    private fun showPopupWindow(window: Window, view: View) {
        // ensure actions affect the right window
        timerTask?.cancel()
        toggleWindowButtonVisibility(true)
        windowControl.activeWindow = window

        val popup = PopupMenu(mainBibleActivity, view)
        popup.setOnMenuItemClickListener { menuItem ->
            resetTouchTimer()
            windowMenuCommandHandler.handleMenuRequest(menuItem)
        }

        val inflater = popup.menuInflater
        inflater.inflate(R.menu.window_popup_menu, popup.menu)

        // enable/disable and set synchronised checkbox
        windowControl.updateOptionsMenu(popup.menu)

        val menuHelper = MenuPopupHelper(mainBibleActivity, popup.menu as MenuBuilder, view)
        menuHelper.setOnDismissListener {
            resetTouchTimer()
            mainBibleActivity.resetSystemUi()
        }

        menuHelper.setForceShowIcon(true)
        menuHelper.show()
    }

    private fun isWebViewShowing(parent: ViewGroup): Boolean {
        val tag = parent.tag
        return tag != null && tag == TAG
    }

    companion object {
        private const val TAG = "DocumentWebViewBuilder"
    }
}
