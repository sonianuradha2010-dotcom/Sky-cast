@file:OptIn(ExperimentalMaterial3Api::class)

package com.skycast.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skycast.weather.WeatherViewModel
import kotlinx.coroutines.delay

/** Bottom sheet: city search, "use my location", saved places and unit toggle. */
@Composable
fun PlaceSheet(vm: WeatherViewModel, onRequestLocation: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        delay(350) // debounce typing
        vm.search(query)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search city") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (vm.searching) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
            )
            Spacer(Modifier.height(12.dp))

            if (query.isBlank()) {
                FilledTonalButton(
                    onClick = {
                        onRequestLocation()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use my location")
                }

                if (vm.places.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Saved places", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        itemsIndexed(vm.places) { index, p ->
                            val isSelected = index == vm.selected
                            ListItem(
                                headlineContent = { Text(p.name) },
                                supportingContent = { if (p.region.isNotBlank()) Text(p.region) },
                                trailingContent = {
                                    IconButton(onClick = { vm.removePlace(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove ${p.name}")
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else androidx.compose.ui.graphics.Color.Transparent
                                ),
                                modifier = Modifier
                                    .clickable {
                                        vm.select(index)
                                        onDismiss()
                                    },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Units", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !vm.fahrenheit,
                        onClick = { vm.setFahrenheit(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("°C · km/h") }
                    SegmentedButton(
                        selected = vm.fahrenheit,
                        onClick = { vm.setFahrenheit(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("°F · mph") }
                }
                Spacer(Modifier.height(24.dp))
            } else {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(vm.results) { _, p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { if (p.region.isNotBlank()) Text(p.region) },
                            leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            modifier = Modifier.clickable {
                                vm.addPlace(p)
                                onDismiss()
                            },
                        )
                    }
                }
                if (!vm.searching && vm.results.isEmpty() && query.trim().length >= 2) {
                    Text(
                        "No matches yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
