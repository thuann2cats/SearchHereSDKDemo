# SearchHereSDKDemo

## Setup steps:
- Be sure to put your .aar Here SDK library in the right folder (as usual)
- Put the .env file in the root of your project and fill in the necessary API keys (as usual)

If the map loads, there are two buttons: **Search** and **Geocoding**. The map centers at Georgia Tech by default, and the search would just query "Pizza" as a demo.

## If you tap Search

If you put a breakpoint at line 84 (`D:\Programming\Android_Studio\SearchHereSDKDemo\app\src\main\java\com\example\searchdemo\SearchExample.kt`), you will get to here:

```kotlin
fun onSearchButtonClicked() {
    // Search for a specific search term and show the results on the map.
    searchExample()

    // Search for auto suggestions and log the results to the console.
    autoSuggestExample()
}
```

### First, searchExample()

In line 95, you can switch to a different search term. Please enter "searchInViewPort" to see how the search is called. Here, the program would retrieve a bunch of search results and put markers for them on the map.

```kotlin
private fun searchExample() {
    val searchTerm = "Pizza"

    _searchStatusFlow.update { "Searching in viewport: $searchTerm" }
    searchInViewport(searchTerm)
}
```

### Second, autoSuggestExample()

Also interesting is line 87, where auto-suggestion is initiated. The function demos three suggestion query, pretending that the user has only typed "P," then "Pi," then "Piz," presumably about to search for "Pizza."

Line 277:

```kotlin
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
```

The `searchEngine.suggestByText` is an asynchronous function (meaning the result is returned at a later time). When the result comes back from Here SDK, the `onSuggestCompleted` callback function is invoked – line 255:

```kotlin
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
```

The `autosuggestResult` is of type `Suggestion` (a Here SDK type). Within it, you can extract the `Place` object. It seems that sometimes, a returned suggestion might not even have a place, hence the `place != null` check. See the above function for details.

Here, the program would just log the suggestions (for each of the three cases) into LogCat. Please filter by "SearchExample" to see the auto-suggestion results.

## If you tap Geocoding

You might want to set a breakpoint at line 104, where the execution will jump into. Basically, you set a `queryString` to be some text address, and the function will call Here SDK to return a `Place` object with proper coordinates and a proper address.

```kotlin
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
```

Enter `geocodeAddressAtLocation()` function for further details.

For example if I set: `val queryString = "988 State St North-West"`  
Here SDK will return a `Place` object, with details such as "988 State St NW, Atlanta, GA 30318-5630, …. GeoCoordinates: 33.78136, -84.39931." Here, the program logged this into LogCat for your information and put a marker at that address.


