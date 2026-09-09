package io.github.dhianapereira.afazeres.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.dhianapereira.afazeres.R
import io.github.dhianapereira.afazeres.data.Category
import io.github.dhianapereira.afazeres.model.CategoryAppearance
import java.util.UUID

internal fun categoryIcon(category: Category): ImageVector = when (category.icon) {
    "work" -> Icons.Outlined.WorkOutline
    "book" -> Icons.Outlined.MenuBook
    "person" -> Icons.Outlined.PersonOutline
    "heart" -> Icons.Outlined.FavoriteBorder
    "finance" -> Icons.Outlined.Paid
    "home" -> Icons.Outlined.Home
    "shopping" -> Icons.Outlined.ShoppingBag
    "fitness" -> Icons.Outlined.FitnessCenter
    "travel" -> Icons.Outlined.Flight
    "music" -> Icons.Outlined.MusicNote
    "code" -> Icons.Outlined.Code
    "food" -> Icons.Outlined.Restaurant
    "pet" -> Icons.Outlined.Pets
    "star" -> Icons.Outlined.StarOutline
    "leaf" -> Icons.Outlined.Eco
    else -> Icons.Outlined.MoreHoriz
}

internal fun categoryIconLabel(icon: String): Int = when (icon) {
    "work" -> R.string.icon_work
    "book" -> R.string.icon_book
    "person" -> R.string.icon_person
    "heart" -> R.string.icon_heart
    "finance" -> R.string.icon_finance
    "home" -> R.string.icon_home
    "shopping" -> R.string.icon_shopping
    "fitness" -> R.string.icon_fitness
    "travel" -> R.string.icon_travel
    "music" -> R.string.icon_music
    "code" -> R.string.icon_code
    "food" -> R.string.icon_food
    "pet" -> R.string.icon_pet
    "star" -> R.string.icon_star
    "leaf" -> R.string.icon_leaf
    else -> R.string.icon_more
}

@Composable internal fun categoryContentColor(category: Category): Color {
    val original = Color(category.color)
    val surface = original.copy(alpha = .14f).compositeOver(MaterialTheme.colorScheme.surface)
    val target = if (surface.luminance() < .5f) Color.White else Color.Black
    for (step in 0..20) {
        val candidate = lerp(original, target, step / 20f)
        val lighter = maxOf(candidate.luminance(), surface.luminance())
        val darker = minOf(candidate.luminance(), surface.luminance())
        if ((lighter + .05f) / (darker + .05f) >= 4.5f) return candidate
    }
    return target
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CategoryEditor(
    category: Category?,
    initialName: String,
    busy: Boolean,
    dismiss: () -> Unit,
    save: (Category) -> Unit,
) {
    val originalDisplayedName by rememberSaveable { mutableStateOf(initialName) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableLongStateOf(category?.color ?: CategoryAppearance.colors.first()) }
    var icon by rememberSaveable { mutableStateOf(category?.icon ?: "work") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("main") }
    val preview = Category(category?.id.orEmpty(), name.trim(), color, icon = icon)
    AppSheet(when (page) {
        "color" -> R.string.category_color
        "icon" -> R.string.category_icon
        else -> if (category == null) R.string.new_category else R.string.edit_category
    }, { if (!busy) dismiss() }) {
        if (page != "main") TextButton(onClick = { page = "main" }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.back))
        }
        Surface(color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryIcon(preview)
                Column {
                    Text(name.ifBlank { stringResource(R.string.category_name) }, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.category_preview), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        when (page) {
            "main" -> {
                SheetField(name, { if (it.length <= 60) name = it }, R.string.category_name, attempted && name.isBlank())
                Surface(onClick = { page = "color" }, color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = CircleShape, color = Color(color), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Spacer(Modifier.size(28.dp)) }
                        Text(stringResource(R.string.category_color), Modifier.weight(1f))
                        Text(CategoryAppearance.hex(color), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.ChevronRight, null)
                    }
                }
                SettingsRow(categoryIcon(preview), R.string.category_icon, stringResource(categoryIconLabel(icon))) { page = "icon" }
                Button(
                    onClick = {
                        attempted = true
                        if (name.isNotBlank()) save(preview.copy(
                            id = category?.id ?: UUID.randomUUID().toString(),
                            name = if (category?.builtIn == true && name.trim() == originalDisplayedName) category.name else name.trim(),
                            builtIn = category?.builtIn == true && name.trim() == originalDisplayedName,
                        ))
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Icon(Icons.Outlined.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.save)) }
            }
            "color" -> CategoryColorPicker(color, { color = it }, { page = "main" })
            "icon" -> {
                Text(stringResource(categoryIconLabel(icon)), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryAppearance.icons.forEach { candidate ->
                        Surface(shape = MaterialTheme.shapes.medium, color = if (icon == candidate) Color(color).copy(alpha = .14f) else MaterialTheme.colorScheme.background, border = if (icon == candidate) BorderStroke(2.dp, categoryContentColor(preview)) else null) {
                            IconToggleButton(checked = icon == candidate, onCheckedChange = { icon = candidate }, modifier = Modifier.size(56.dp)) {
                                Icon(categoryIcon(preview.copy(icon = candidate)), stringResource(categoryIconLabel(candidate)), tint = categoryContentColor(preview))
                            }
                        }
                    }
                }
                Button(onClick = { page = "main" }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.confirm_choice)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CategoryColorPicker(color: Long, change: (Long) -> Unit, done: () -> Unit) {
    var hex by rememberSaveable { mutableStateOf(CategoryAppearance.hex(color)) }
    // Keep hue when saturation/value reach zero so the sliders remain independently usable.
    val initialHsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(), it) } }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    fun choose(value: Long) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(value.toInt(), it) }
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
        hex = CategoryAppearance.hex(value)
        change(value)
    }
    fun updateSliders() {
        val value = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)).toLong() and 0xFFFFFFFFL
        hex = CategoryAppearance.hex(value)
        change(value)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryAppearance.palette.forEach { candidate ->
            IconToggleButton(checked = color == candidate, onCheckedChange = { choose(candidate) }, modifier = Modifier.size(48.dp).background(Color(candidate), CircleShape)) {
                Icon(if (color == candidate) Icons.Outlined.Check else Icons.Outlined.Circle, stringResource(R.string.color_value, CategoryAppearance.hex(candidate)), tint = if (color == candidate) { if (Color(candidate).luminance() > .4f) Color.Black else Color.White } else Color.Transparent)
            }
        }
    }
    val hueLabel = stringResource(R.string.color_hue)
    val saturationLabel = stringResource(R.string.color_saturation)
    val brightnessLabel = stringResource(R.string.color_brightness)
    Text(hueLabel, style = MaterialTheme.typography.labelLarge)
    Slider(value = hue, onValueChange = { hue = it; updateSliders() }, valueRange = 0f..360f, modifier = Modifier.fillMaxWidth().semantics { contentDescription = hueLabel })
    Text(saturationLabel, style = MaterialTheme.typography.labelLarge)
    Slider(value = saturation, onValueChange = { saturation = it; updateSliders() }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = saturationLabel })
    Text(brightnessLabel, style = MaterialTheme.typography.labelLarge)
    Slider(value = brightness, onValueChange = { brightness = it; updateSliders() }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = brightnessLabel })
    val valid = CategoryAppearance.parseHex(hex) != null
    OutlinedTextField(
        value = hex,
        onValueChange = { if (it.length <= 7) { hex = it; CategoryAppearance.parseHex(it)?.let { value -> choose(value) } } },
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium,
        label = { Text(stringResource(R.string.color_hex)) }, isError = !valid,
        supportingText = { Text(stringResource(if (valid) R.string.color_hex_hint else R.string.color_hex_error)) },
    )
    Button(onClick = done, enabled = valid, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.confirm_choice)) }
}
