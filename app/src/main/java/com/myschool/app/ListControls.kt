package com.myschool.app

import android.view.View
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/** Fill a ChipGroup with single-select options; first one is selected. Calls onSelect(index). */
fun ChipGroup.setOptions(labels: List<String>, onSelect: (Int) -> Unit) {
    removeAllViews()
    labels.forEachIndexed { i, label ->
        val chip = Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
            id = View.generateViewId()
            text = label
            isCheckable = true
            isCheckedIconVisible = false
            setOnClickListener { onSelect(i) }
        }
        addView(chip)
        if (i == 0) chip.isChecked = true
    }
}

/** Holds the full list + current filter/sort, returns what to show. */
class ListState<T>(private var all: List<T>) {
    var filter: (T) -> Boolean = { true }
    var sorts: List<Pair<String, Comparator<T>>> = emptyList()
    private var sortIndex = 0

    val sortLabel get() = sorts.getOrNull(sortIndex)?.first.orEmpty()
    fun nextSort() { if (sorts.isNotEmpty()) sortIndex = (sortIndex + 1) % sorts.size }
    fun submit(items: List<T>) { all = items }

    fun result(): List<T> {
        val filtered = all.filter(filter)
        return sorts.getOrNull(sortIndex)?.let { filtered.sortedWith(it.second) } ?: filtered
    }
}
