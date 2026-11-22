package com.superessence.ultrafichiers.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.superessence.ultrafichiers.R
import com.superessence.ultrafichiers.data.Trash
import com.superessence.ultrafichiers.work.Notif
import com.superessence.ultrafichiers.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UltraApp() {
    var splash by remember { mutableStateOf(true) }
    AnimatedContent(splash, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "splash") { s ->
        if (s) SplashScreen { splash = false } else PermissionGate { Main() }
    }
}

private fun hasAccess(ctx: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAccess(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasAccess(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasAccess(ctx) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (granted) {
        LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
        content(); return
    }
    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(110.dp).clip(RoundedCornerShape(28.dp)))
        Spacer(Modifier.height(20.dp))
        Text("Accès aux fichiers", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Text(
            "Ultra Fichiers a besoin de l'accès à tous les fichiers pour les gérer, trouver les doublons, nettoyer et optimiser vos vidéos. Rien ne quitte votre téléphone.",
            color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp)
        )
        Button(
            {
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                    } catch (e: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            },
            Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color.Black)
        ) { Text("Autoriser l'accès", fontSize = 17.sp) }
    }
}

@Composable
private fun Main() {
    val vm: AppViewModel = viewModel()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        Notif.channel(ctx)
        vm.refreshStorage(); vm.open(vm.dir); vm.index()
        Scheduler.apply(ctx)
        withContext(Dispatchers.IO) { vm.prefs.totalFreed += Trash.purgeOld(vm.prefs.trashDays) }
        vm.runScan()
    }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    BackHandler(enabled = vm.overlays.isNotEmpty() || vm.tab != 0 || (vm.tab == 1)) {
        when {
            vm.pop() -> {}
            vm.tab == 1 && vm.up() -> {}
            else -> vm.tab = 0
        }
    }
    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (vm.overlays.isEmpty()) NavigationBar(containerColor = Surface1) {
                listOf(
                    Triple("Accueil", Icons.Rounded.Home, 0), Triple("Fichiers", Icons.Rounded.Folder, 1),
                    Triple("Nettoyer", Icons.Rounded.CleaningServices, 2), Triple("Vidéos", Icons.Rounded.VideoLibrary, 3),
                ).forEach { (l, i, t) ->
                    NavigationBarItem(
                        vm.tab == t, { vm.tab = t }, { Icon(i, l) }, label = { Text(l) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black, indicatorColor = Yellow, selectedTextColor = Yellow,
                            unselectedIconColor = Muted, unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (vm.tab) {
                0 -> HomeScreen(vm)
                1 -> BrowserScreen(vm)
                2 -> CleanerScreen(vm)
                else -> VideoScreen(vm)
            }
            vm.overlays.lastOrNull()?.let { o ->
                Box(Modifier.fillMaxSize().background(Bg)) {
                    when (o) {
                        is Overlay.Category -> CategoryScreen(vm, o.cat)
                        Overlay.Trash -> TrashScreen(vm)
                        Overlay.Settings -> SettingsScreen(vm)
                        Overlay.VideoPicker -> VideoPickerScreen(vm)
                    }
                }
            }
        }
    }
}
