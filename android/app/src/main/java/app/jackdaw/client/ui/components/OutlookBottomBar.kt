package app.jackdaw.client.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jackdaw.client.ui.navigation.Screen

@Composable
fun OutlookBottomNavigationBar(
    currentRoute: String,
    unreadCount: Int,
    onNavigateToMail: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val isMailSelected = currentRoute == Screen.MailList.route
        val isCalendarSelected = currentRoute == Screen.Calendar.route

        NavigationBarItem(
            selected = isMailSelected,
            onClick = onNavigateToMail,
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Email,
                        contentDescription = "Почта"
                    )
                }
            },
            label = {
                Text(
                    text = "Почта",
                    fontWeight = if (isMailSelected) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )

        NavigationBarItem(
            selected = isCalendarSelected,
            onClick = onNavigateToCalendar,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DateRange,
                    contentDescription = "Календарь"
                )
            },
            label = {
                Text(
                    text = "Календарь",
                    fontWeight = if (isCalendarSelected) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )
    }
}
