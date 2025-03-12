package com.example.searchdemo

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import androidx.annotation.NonNull
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.here.sdk.core.Anchor2D
import com.here.sdk.core.CustomMetadataValue
import com.here.sdk.core.GeoBox
import com.here.sdk.core.GeoCoordinates
import com.here.sdk.core.LanguageCode
import com.here.sdk.core.Metadata
import com.here.sdk.core.Point2D
import com.here.sdk.core.Rectangle2D
import com.here.sdk.core.Size2D
import com.here.sdk.core.errors.InstantiationErrorException
import com.here.sdk.gestures.GestureState
import com.here.sdk.gestures.LongPressListener
import com.here.sdk.gestures.TapListener
import com.here.sdk.mapview.MapCamera
import com.here.sdk.mapview.MapImage
import com.here.sdk.mapview.MapImageFactory
import com.here.sdk.mapview.MapMarker
import com.here.sdk.mapview.MapMeasure
import com.here.sdk.mapview.MapPickResult
import com.here.sdk.mapview.MapScene
import com.here.sdk.mapview.MapView
import com.here.sdk.mapview.MapViewBase
import com.here.sdk.search.Address
import com.here.sdk.search.AddressQuery
import com.here.sdk.search.Place
import com.here.sdk.search.SearchCallback
import com.here.sdk.search.SearchEngine
import com.here.sdk.search.SearchError
import com.here.sdk.search.SearchOptions
import com.here.sdk.search.SuggestCallback
import com.here.sdk.search.Suggestion
import com.here.sdk.search.TextQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SearchExample(private val context: Context, private val mapView: MapView) {

    private val camera: MapCamera = mapView.camera
    private val _mapMarkers = mutableStateListOf<MapMarker>()
    val mapMarkers = _mapMarkers
    private val searchEngine: SearchEngine
    
    private val _searchStatusFlow = MutableStateFlow("")
    val searchStatusFlow: StateFlow<String> = _searchStatusFlow.asStateFlow()

    val georgiaTechLatitude = 33.7756
    val georgiaTechLongitude = -84.3963

    init {
        val distanceInMeters = 1000 * 10.0
        val mapMeasureZoom = MapMeasure(MapMeasure.Kind.DISTANCE, distanceInMeters)
        camera.lookAt(GeoCoordinates(georgiaTechLatitude, georgiaTechLongitude), mapMeasureZoom)

        try {
            searchEngine = SearchEngine()
        } catch (e: InstantiationErrorException) {
            throw RuntimeException("Initialization of SearchEngine failed: ${e.error.name}")
        }

        setTapGestureHandler()
        setLongPressGestureHandler()
        
        _searchStatusFlow.update { "Long press on map to get the address for that position using reverse geocoding." }
    }

    fun onSearchButtonClicked() {
        // Search for a specific search term and show the results on the map.
        searchExample()

        // Search for auto suggestions and log the results to the console.
        autoSuggestExample()
    }

    fun onGeocodeButtonClicked() {
        // Search for the location that belongs to an address and show it on the map.
        geocodeAnAddress()
    }

    private fun searchExample() {
        val searchTerm = "Pizza"

        _searchStatusFlow.update { "Searching in viewport: $searchTerm" }
        searchInViewport(searchTerm)
    }

    private fun geocodeAnAddress() {
        // Set map to expected location.
        val geoCoordinates = GeoCoordinates(georgiaTechLatitude, georgiaTechLongitude)
        val distanceInMeters = 1000 * 7.0
        val mapMeasureZoom = MapMeasure(MapMeasure.Kind.DISTANCE, distanceInMeters)
        camera.lookAt(geoCoordinates, mapMeasureZoom)

        val queryString = "988 State St North-West"

        _searchStatusFlow.update { "Finding locations for: $queryString. Tap marker to see the coordinates." }

        geocodeAddressAtLocation(queryString, geoCoordinates)
    }

    private fun setTapGestureHandler() {
        // Use proper TapListener implementation
        mapView.gestures.setTapListener(object : TapListener {
            override fun onTap(touchPoint: Point2D) {
                pickMapMarker(touchPoint)
            }
        })
    }

    private fun setLongPressGestureHandler() {
        // Use proper LongPressListener implementation
        mapView.gestures.setLongPressListener(object : LongPressListener {
            override fun onLongPress(state: GestureState, touchPoint: Point2D) {
                if (state == GestureState.BEGIN) {
                    mapView.viewToGeoCoordinates(touchPoint)?.let { geoCoordinates ->
                        addPoiMapMarker(geoCoordinates)
                        getAddressForCoordinates(geoCoordinates)
                    }
                }
            }
        })
    }

    private fun getAddressForCoordinates(geoCoordinates: GeoCoordinates) {
        val reverseGeocodingOptions = SearchOptions().apply {
            languageCode = LanguageCode.EN_GB
            maxItems = 1
        }

        searchEngine.searchByCoordinates(geoCoordinates, reverseGeocodingOptions, addressSearchCallback)
    }

    private val addressSearchCallback = object : SearchCallback {
        override fun onSearchCompleted(searchError: SearchError?, list: List<Place>?) {
            if (searchError != null) {
                showDialog("Reverse geocoding", "Error: $searchError")
                return
            }

            // If error is null, list is guaranteed to be not empty.
            list?.firstOrNull()?.let {
                showDialog("Reverse geocoded address:", it.address.addressText)
            }
        }
    }

    private fun pickMapMarker(touchPoint: Point2D) {
        val originInPixels = Point2D(touchPoint.x, touchPoint.y)
        val sizeInPixels = Size2D(1.0, 1.0)
        val rectangle = Rectangle2D(originInPixels, sizeInPixels)

        // Creates a list of map content type from which the results will be picked.
        val contentTypesToPickFrom = ArrayList<MapScene.MapPickFilter.ContentType>()

        // MAP_ITEMS is used when picking map items such as MapMarker, MapPolyline, MapPolygon etc.
        contentTypesToPickFrom.add(MapScene.MapPickFilter.ContentType.MAP_ITEMS)
        val filter = MapScene.MapPickFilter(contentTypesToPickFrom)

        // Correct implementation of MapPickCallback
        mapView.pick(filter, rectangle, object : MapViewBase.MapPickCallback {
            override fun onPickMap(pickResult: MapPickResult?) {
                if (pickResult == null) {
                    // An error occurred while performing the pick operation.
                    return
                }
                
                // Use safe call (?.) operator to safely access mapItems property
                val mapMarkerList = pickResult.mapItems?.markers ?: emptyList()
                if (mapMarkerList.isEmpty()) {
                    return
                }
                
                val topmostMapMarker = mapMarkerList[0]
                val metadata = topmostMapMarker.metadata
                
                if (metadata != null) {
                    val customMetadataValue = metadata.getCustomValue("key_search_result")
                    if (customMetadataValue != null) {
                        val searchResultMetadata = customMetadataValue as SearchResultMetadata
                        val title = searchResultMetadata.searchResult.title
                        val vicinity = searchResultMetadata.searchResult.address.addressText
                        showDialog("Picked Search Result", "$title. Vicinity: $vicinity")
                        return
                    }
                }
                
                showDialog("Picked Map Marker",
                    "Geographic coordinates: ${topmostMapMarker.coordinates.latitude}, ${topmostMapMarker.coordinates.longitude}")
            }
        })
    }

    private fun searchInViewport(queryString: String) {
        clearMap()

        val viewportGeoBox = getMapViewGeoBox()
        val queryArea = TextQuery.Area(viewportGeoBox)
        val query = TextQuery(queryString, queryArea)

        val searchOptions = SearchOptions().apply {
            languageCode = LanguageCode.EN_US
            maxItems = 30
        }

        searchEngine.searchByText(query, searchOptions, querySearchCallback)
    }

    private val querySearchCallback = object : SearchCallback {
        override fun onSearchCompleted(searchError: SearchError?, list: List<Place>?) {
            if (searchError != null) {
                showDialog("Search", "Error: $searchError")
                return
            }

            // If error is null, list is guaranteed to be not empty.
            list?.let {
                showDialog("Search", "Results: ${it.size}")

                // Add new marker for each search result on map.
                for (searchResult in it) {
                    val metadata = Metadata()
                    metadata.setCustomValue("key_search_result", SearchResultMetadata(searchResult))
                    // Note: getGeoCoordinates() may return null only for Suggestions.
                    searchResult.geoCoordinates?.let { coordinates ->
                        addPoiMapMarker(coordinates, metadata)
                    }
                }
            }
        }
    }

    private class SearchResultMetadata(val searchResult: Place) : CustomMetadataValue {
        @NonNull
        override fun getTag(): String {
            return "SearchResult Metadata"
        }
    }

    private val autosuggestCallback = object : SuggestCallback {
        override fun onSuggestCompleted(searchError: SearchError?, list: List<Suggestion>?) {
            if (searchError != null) {
                Log.d(TAG, "Autosuggest Error: ${searchError.name}")
                return
            }

            list?.let {
                Log.d(TAG, "Autosuggest results: ${it.size}")

                for (autosuggestResult in it) {
                    var addressText = "Not a place."
                    val place = autosuggestResult.place
                    if (place != null) {
                        addressText = place.address.addressText
                    }

                    Log.d(TAG, "Autosuggest result: ${autosuggestResult.title}, addressText: $addressText")
                }
            }
        }
    }

    private fun autoSuggestExample() {
        val centerGeoCoordinates = getMapViewCenter()

        val searchOptions = SearchOptions().apply {
            languageCode = LanguageCode.EN_US
            maxItems = 5
        }

        val queryArea = TextQuery.Area(centerGeoCoordinates)

        // Simulate a user typing a search term.
        searchEngine.suggestByText(
            TextQuery("p", queryArea), // User typed "p".
            searchOptions,
            autosuggestCallback
        )

        searchEngine.suggestByText(
            TextQuery("pi", queryArea), // User typed "pi".
            searchOptions,
            autosuggestCallback
        )

        searchEngine.suggestByText(
            TextQuery("piz", queryArea), // User typed "piz".
            searchOptions,
            autosuggestCallback
        )
    }

    private fun geocodeAddressAtLocation(queryString: String, geoCoordinates: GeoCoordinates) {
        clearMap()

        val query = AddressQuery(queryString, geoCoordinates)

        val options = SearchOptions().apply {
            languageCode = LanguageCode.DE_DE
            maxItems = 30
        }

        searchEngine.searchByAddress(query, options, geocodeAddressSearchCallback)
    }

    private val geocodeAddressSearchCallback = object : SearchCallback {
        override fun onSearchCompleted(searchError: SearchError?, list: List<Place>?) {
            if (searchError != null) {
                showDialog("Geocoding", "Error: $searchError")
                return
            }

            list?.forEach { geocodingResult ->
                // Note: getGeoCoordinates() may return null only for Suggestions.
                val geoCoordinates = geocodingResult.geoCoordinates
                val address = geocodingResult.address
                geoCoordinates?.let {
                    val locationDetails = "${address.addressText}. GeoCoordinates: ${it.latitude}, ${it.longitude}"
                    Log.d(TAG, "GeocodingResult: $locationDetails")
                    addPoiMapMarker(it)
                }
            }

            list?.let {
                showDialog("Geocoding result", "Size: ${it.size}")
            }
        }
    }

    private fun addPoiMapMarker(geoCoordinates: GeoCoordinates) {
        val mapMarker = createPoiMapMarker(geoCoordinates)
        mapView.mapScene.addMapMarker(mapMarker)
        _mapMarkers.add(mapMarker)
    }

    private fun addPoiMapMarker(geoCoordinates: GeoCoordinates, metadata: Metadata) {
        val mapMarker = createPoiMapMarker(geoCoordinates)
        mapMarker.metadata = metadata
        mapView.mapScene.addMapMarker(mapMarker)
        _mapMarkers.add(mapMarker)
    }

    fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: 32, // Default size if -1
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 32, // Default size if -1
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    private fun getFallbackDrawable(context: Context): Bitmap {
        return getBitmapFromVectorDrawable(context, android.R.drawable.ic_menu_mylocation)!!
    }


    private fun createPoiMapMarker(geoCoordinates: GeoCoordinates): MapMarker {
//        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.poi)
//        Log.d("BitmapCheck", "Bitmap: $bitmap")
//        val mapImage = MapImageFactory.fromResource(context.resources, R.drawable.poi_png)
        val poiBitmap = getBitmapFromVectorDrawable(context, R.drawable.poi)

        val mapImage = MapImageFactory.fromBitmap(poiBitmap ?: getFallbackDrawable(context))

        return MapMarker(geoCoordinates, mapImage, Anchor2D(0.5, 1.0))
    }

    private fun getMapViewCenter(): GeoCoordinates {
        return mapView.camera.state.targetCoordinates
    }

    private fun getMapViewGeoBox(): GeoBox {
        val mapViewWidthInPixels = mapView.width
        val mapViewHeightInPixels = mapView.height
        val bottomLeftPoint2D = Point2D(0.0, mapViewHeightInPixels.toDouble())
        val topRightPoint2D = Point2D(mapViewWidthInPixels.toDouble(), 0.0)

        val southWestCorner = mapView.viewToGeoCoordinates(bottomLeftPoint2D)
        val northEastCorner = mapView.viewToGeoCoordinates(topRightPoint2D)

        if (southWestCorner == null || northEastCorner == null) {
            throw RuntimeException("GeoBox creation failed, corners are null.")
        }

        // Note: This algorithm assumes an unrotated map view.
        return GeoBox(southWestCorner, northEastCorner)
    }

    fun clearMap() {
        _mapMarkers.forEach { mapMarker ->
            mapView.mapScene.removeMapMarker(mapMarker)
        }
        _mapMarkers.clear()
    }

    private fun showDialog(title: String, message: String) {
        _searchStatusFlow.update { message }
//        MaterialAlertDialogBuilder(context)
//            .setTitle(title)
//            .setMessage(message)
//            .setPositiveButton("OK", null)
//            .show()
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        private const val TAG = "SearchExample"
    }
}
