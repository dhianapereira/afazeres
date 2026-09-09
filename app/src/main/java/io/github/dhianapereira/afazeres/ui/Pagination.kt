package io.github.dhianapereira.afazeres.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dhianapereira.afazeres.R

internal const val PAGE_SIZE = 20
internal fun pageCount(size: Int): Int = ((size.coerceAtLeast(1) - 1) / PAGE_SIZE) + 1
internal fun validPage(page: Int, size: Int): Int = page.coerceIn(0, pageCount(size) - 1)
internal fun <T> pageItems(items: List<T>, page: Int): List<T> = items.drop(validPage(page, items.size) * PAGE_SIZE).take(PAGE_SIZE)

@Composable
internal fun Pagination(page: Int, size: Int, enabled: Boolean = true, onPage: (Int) -> Unit) {
    if (size <= PAGE_SIZE) return
    val current = validPage(page, size)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onPage(current - 1) }, enabled = enabled && current > 0) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.previous_page))
        }
        Text(stringResource(R.string.page_position, current + 1, pageCount(size)), style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = { onPage(current + 1) }, enabled = enabled && current < pageCount(size) - 1) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_page))
        }
    }
}
