package com.wyldsoft.notes.views

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.wyldsoft.notes.classes.LocalSnackContext
import com.wyldsoft.notes.services.GoogleDriveBackupService
import com.wyldsoft.notes.ui.theme.NotesTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity for handling backup to Google Drive.
 *
 * This activity allows users to sign in to Google Drive and
 * backup their notes database.
 */
class BackupActivity : ComponentActivity() {
    private lateinit var backupService: GoogleDriveBackupService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backupService = GoogleDriveBackupService(this)
        backupService.setupGoogleSignIn(this)

        setContent {
            NotesTheme {
                BackupScreen(backupService = backupService)
            }
        }
    }
}

/**
 * Composable for the backup screen.
 *
 * @param navController Optional navigation controller for navigation
 * @param backupService The Google Drive backup service
 */
@Composable
fun BackupScreen(
    navController: NavController? = null,
    backupService: GoogleDriveBackupService
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackState = LocalSnackContext.current

    val backupState by backupService.backupState.collectAsState()
    val errorMessage by backupService.errorMessage.collectAsState()

    var isSignedIn by remember { mutableStateOf(backupService.isSignedIn()) }

    // Launcher for Google Sign-In intent
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        backupService.handleSignInResult(result.data)
        isSignedIn = backupService.isSignedIn()
    }

    // Monitor backup state to show messages
    LaunchedEffect(backupState) {
        when (backupState) {
            GoogleDriveBackupService.BackupState.SUCCESS -> {
                scope.launch {
                    snackState.displaySnack(
                        com.wyldsoft.notes.classes.SnackConf(
                            text = "Backup completed successfully",
                            duration = 3000
                        )
                    )
                    backupService.resetState()
                }
            }
            GoogleDriveBackupService.BackupState.ERROR -> {
                errorMessage?.let { error ->
                    scope.launch {
                        snackState.displaySnack(
                            com.wyldsoft.notes.classes.SnackConf(
                                text = error,
                                duration = 3000
                            )
                        )
                        backupService.resetState()
                    }
                }
            }
            else -> { /* Other states don't need snack messages */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup and Restore") },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colors.primary
            )

            Text(
                text = "Google Drive Backup",
                style = MaterialTheme.typography.h5
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Google Account Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Google Account",
                        style = MaterialTheme.typography.h6
                    )

                    if (isSignedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Green
                            )
                            Text("Signed in")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    backupService.signOut()
                                    isSignedIn = backupService.isSignedIn()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color.LightGray
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sign Out"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                    } else {
                        Text("Not signed in")

                        Button(
                            onClick = {
                                backupService.signIn(context as ComponentActivity, signInLauncher)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = "Sign In"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In with Google")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Backup Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Manual Backup",
                        style = MaterialTheme.typography.h6
                    )

                    Text(
                        text = "This will create a backup of your notes database in your Google Drive."
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                if (!isSignedIn) {
                                    backupService.signIn(context as ComponentActivity, signInLauncher)
                                } else {
                                    backupService.backupDatabase()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = backupState != GoogleDriveBackupService.BackupState.UPLOADING &&
                                backupState != GoogleDriveBackupService.BackupState.PREPARING
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Backup"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup Now")
                    }

                    // Show progress if backup is in progress
                    if (backupState == GoogleDriveBackupService.BackupState.UPLOADING ||
                        backupState == GoogleDriveBackupService.BackupState.PREPARING) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        Text(
                            text = when (backupState) {
                                GoogleDriveBackupService.BackupState.PREPARING -> "Preparing backup..."
                                GoogleDriveBackupService.BackupState.UPLOADING -> "Uploading to Google Drive..."
                                else -> ""
                            },
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
        }
    }
}