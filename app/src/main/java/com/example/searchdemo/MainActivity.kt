package com.example.searchdemo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.searchdemo.ui.theme.SearchDemoTheme
import com.here.sdk.core.engine.SDKBuildInformation
import com.here.sdk.mapview.MapError
import com.here.sdk.mapview.MapScene
import com.here.sdk.mapview.MapScheme
import com.here.sdk.mapview.MapView

class MainActivity : ComponentActivity() {
    
    private val TAG = MainActivity::class.java.simpleName
    private lateinit var permissionsRequestor: PermissionsRequestor
    private var searchExample: SearchExample? = null
    private var mapView: MapView? = null
    private val snackbarHostState = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // The HERE SDK is already initialized in the HereApplication class
        
        setContent {
            SearchDemoTheme {
                var mapViewInitialized by remember { mutableStateOf(false) }
                
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.poi),
                            contentDescription = "POI"
                        )
                        Column(modifier = Modifier.fillMaxSize()) {
                            // MapView container
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                AndroidView(
                                    factory = { context ->
                                        MapView(context).apply {
                                            mapView = this
                                            this.onCreate(savedInstanceState)

                                            Log.d(TAG, "HERE SDK version: ${SDKBuildInformation.sdkVersion().versionName}")

                                            // Request permissions and load map scene after permissions are granted
                                            permissionsRequestor = PermissionsRequestor(this@MainActivity)
                                            handleAndroidPermissions()
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                searchExample?.let { searchExample ->
                                    val statusMessage by searchExample.searchStatusFlow.collectAsStateWithLifecycle()
                                    if (statusMessage.isNotEmpty()) {
                                        Text(
                                            text = statusMessage,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(16.dp),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }

                            // Button controls
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Button(
                                    onClick = { searchExample?.onSearchButtonClicked() },
                                    enabled = mapViewInitialized,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Search")
                                }

                                Spacer(modifier = Modifier.padding(8.dp))

                                Button(
                                    onClick = { searchExample?.onGeocodeButtonClicked() },
                                    enabled = mapViewInitialized,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Geocode")
                                }
                            }
                        }
                    }
                }
                
                LaunchedEffect(Unit) {
                    mapView?.let {
                        mapViewInitialized = true
                    }
                }
            }
        }
    }
    
    private fun handleAndroidPermissions() {
        permissionsRequestor.request(object : PermissionsRequestor.ResultListener {
            override fun permissionsGranted() {
                loadMapScene()
            }

            override fun permissionsDenied() {
                Log.e(TAG, "Permissions denied by user.")
            }
        })
    }
    
    private fun loadMapScene() {
        mapView?.mapScene?.loadScene(MapScheme.NORMAL_DAY) { mapError: MapError? ->
            if (mapError == null) {
                mapView?.let { mapView ->
                    searchExample = SearchExample(this, mapView)
                }
            } else {
                Log.d(TAG, "onLoadScene failed: $mapError")
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsRequestor.onRequestPermissionsResult(requestCode, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    
    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }
    
    override fun onResume() {
        mapView?.onResume()
        super.onResume()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    
    override fun onDestroy() {
        mapView?.onDestroy()
        super.onDestroy()
        // No need to dispose HERE SDK here since it's handled in HereApplication.onTerminate()
    }
}