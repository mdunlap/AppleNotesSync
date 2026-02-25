package com.applenotesync.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.applenotesync.app.ui.NoteDetailScreen
import com.applenotesync.app.ui.NotesListScreen
import com.applenotesync.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "notes") {
                        composable("notes") {
                            NotesListScreen(
                                onNoteClick = { noteId ->
                                    navController.navigate("note/$noteId")
                                },
                                onSettingsClick = {
                                    navController.navigate("settings")
                                },
                            )
                        }
                        composable(
                            "note/{noteId}",
                            arguments = listOf(navArgument("noteId") { type = NavType.IntType }),
                        ) { backStackEntry ->
                            val noteId = backStackEntry.arguments?.getInt("noteId") ?: return@composable
                            NoteDetailScreen(
                                noteId = noteId,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic && isSystemInDarkTheme() -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        isSystemInDarkTheme() -> darkColorScheme(
            primary = Color(0xFFE8B931),
            primaryContainer = Color(0xFF534600),
            secondary = Color(0xFFD4C68D),
            surface = Color(0xFF1C1B17),
            background = Color(0xFF1C1B17),
        )
        else -> lightColorScheme(
            primary = Color(0xFF6D5E00),
            primaryContainer = Color(0xFFF9E547),
            secondary = Color(0xFF655E40),
            surface = Color(0xFFFFF9EE),
            background = Color(0xFFFFF9EE),
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
