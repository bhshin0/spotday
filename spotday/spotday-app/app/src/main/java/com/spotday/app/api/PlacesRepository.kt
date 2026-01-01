package com.spotday.app.api

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.spotday.app.BuildConfig
import com.spotday.app.model.Place as AppPlace
import com.spotday.app.model.PlaceType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlacesRepository(private val context: Context) {
    private val placesClient: PlacesClient

    init {
        if (!Places.isInitialized()) {
            Places.initialize(context, BuildConfig.PLACES_API_KEY)
        }
        placesClient = Places.createClient(context)
    }

    // San Francisco center coordinates
    private val SF_CENTER = LatLng(37.7749, -122.4194)

    suspend fun searchMuseums(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for museums in SF")
        return listOf(
            // North SF - Fisherman's Wharf / Marina
            AppPlace("maritime_museum", "Maritime Museum", PlaceType.MUSEUM, 37.8088, -122.4229, 4.5f, true, 2, 15),
            AppPlace("wax_museum", "Wax Museum at Fisherman's Wharf", PlaceType.MUSEUM, 37.8098, -122.4166, 4.2f, true, 2, 25),
            AppPlace("exploratorium", "Exploratorium", PlaceType.MUSEUM, 37.8014, -122.3975, 4.6f, true, 2, 30),
            AppPlace("palace_fine_arts", "Palace of Fine Arts", PlaceType.MUSEUM, 37.8033, -122.4477, 4.7f, true, 1, 0),
            AppPlace("fort_mason", "Fort Mason Center", PlaceType.MUSEUM, 37.8055, -122.4315, 4.4f, true, 1, 10),
            
            // Central SF - Downtown / SoMa
            AppPlace("sfmoma", "San Francisco Museum of Modern Art", PlaceType.MUSEUM, 37.7857, -122.4011, 4.6f, true, 2, 25),
            AppPlace("jewish_museum", "Contemporary Jewish Museum", PlaceType.MUSEUM, 37.7847, -122.4020, 4.3f, true, 2, 15),
            AppPlace("cartoon_art", "Cartoon Art Museum", PlaceType.MUSEUM, 37.7826, -122.4025, 4.4f, true, 2, 10),
            AppPlace("museum_craft", "Museum of Craft and Design", PlaceType.MUSEUM, 37.7751, -122.3985, 4.2f, true, 2, 12),
            AppPlace("asian_art", "Asian Art Museum", PlaceType.MUSEUM, 37.7803, -122.4158, 4.5f, true, 2, 20),
            AppPlace("afric_diaspora", "Museum of the African Diaspora", PlaceType.MUSEUM, 37.7858, -122.4012, 4.3f, true, 2, 15),
            
            // Central - Mission / Castro
            AppPlace("mission_cultural", "Mission Cultural Center", PlaceType.MUSEUM, 37.7485, -122.4192, 4.3f, true, 1, 5),
            AppPlace("womens_building", "Women's Building Mural", PlaceType.MUSEUM, 37.7564, -122.4202, 4.5f, true, 1, 0),
            AppPlace("glbt_history", "GLBT Historical Society Museum", PlaceType.MUSEUM, 37.7615, -122.4345, 4.6f, true, 1, 10),
            AppPlace("balmy_alley", "Balmy Alley Murals", PlaceType.MUSEUM, 37.7475, -122.4158, 4.7f, true, 1, 0),
            AppPlace("precita_eyes", "Precita Eyes Mural Arts Center", PlaceType.MUSEUM, 37.7478, -122.4148, 4.5f, true, 1, 15),
            
            // Golden Gate Park Area
            AppPlace("deyoung", "de Young Museum", PlaceType.MUSEUM, 37.7714, -122.4686, 4.6f, true, 2, 25),
            AppPlace("calacdemy", "California Academy of Sciences", PlaceType.MUSEUM, 37.7699, -122.4661, 4.7f, true, 2, 30),
            AppPlace("legion_honor", "Legion of Honor", PlaceType.MUSEUM, 37.7849, -122.5001, 4.6f, true, 2, 20),
            AppPlace("japanese_tea", "Japanese Tea Garden", PlaceType.MUSEUM, 37.7702, -122.4699, 4.6f, true, 1, 10),
            AppPlace("conservatory", "Conservatory of Flowers", PlaceType.MUSEUM, 37.7727, -122.4608, 4.6f, true, 1, 10),
            AppPlace("botanical_garden", "SF Botanical Garden", PlaceType.MUSEUM, 37.7677, -122.4736, 4.7f, true, 1, 10),
            
            // Richmond / Sunset
            AppPlace("musee_mecanique", "Musée Mécanique", PlaceType.MUSEUM, 37.8090, -122.4185, 4.5f, true, 1, 5),
            AppPlace("cliff_house", "Cliff House Visitor Center", PlaceType.MUSEUM, 37.7783, -122.5139, 4.2f, true, 1, 0),
            AppPlace("sutro_baths", "Sutro Baths Museum", PlaceType.MUSEUM, 37.7805, -122.5135, 4.4f, true, 1, 0),
            
            // Financial District / Embarcadero
            AppPlace("wells_fargo", "Wells Fargo History Museum", PlaceType.MUSEUM, 37.7933, -122.4012, 4.3f, true, 1, 0),
            AppPlace("museum_money", "Museum of Money", PlaceType.MUSEUM, 37.7888, -122.4034, 4.1f, true, 1, 0),
            AppPlace("railway_museum", "SF Railway Museum", PlaceType.MUSEUM, 37.7918, -122.3941, 4.4f, true, 1, 0),
            
            // Various Neighborhoods
            AppPlace("chinese_historical", "Chinese Historical Society", PlaceType.MUSEUM, 37.7942, -122.4061, 4.4f, true, 1, 10),
            AppPlace("beat_museum", "Beat Museum", PlaceType.MUSEUM, 37.7974, -122.4082, 4.3f, true, 1, 10),
            AppPlace("haas_lilienthal", "Haas-Lilienthal House", PlaceType.MUSEUM, 37.7912, -122.4253, 4.5f, true, 1, 15),
            AppPlace("octagon_house", "Octagon House Museum", PlaceType.MUSEUM, 37.8004, -122.4309, 4.3f, true, 1, 10),
            AppPlace("diego_rivera", "Diego Rivera Gallery", PlaceType.MUSEUM, 37.7218, -122.4714, 4.4f, true, 1, 5),
            AppPlace("randall_museum", "Randall Museum", PlaceType.MUSEUM, 37.7624, -122.4389, 4.5f, true, 1, 5),
            AppPlace("sf_fire", "SF Fire Department Museum", PlaceType.MUSEUM, 37.7845, -122.4217, 4.6f, true, 1, 0),
            AppPlace("society_pioneer", "Society of California Pioneers", PlaceType.MUSEUM, 37.7803, -122.4027, 4.3f, true, 1, 5),
            AppPlace("mexican_museum", "Mexican Museum", PlaceType.MUSEUM, 37.8056, -122.4322, 4.2f, true, 2, 15),
            AppPlace("sf_city_hall", "SF City Hall Tours", PlaceType.MUSEUM, 37.7793, -122.4193, 4.7f, true, 1, 10),
            AppPlace("sf_public_lib", "SF Main Library History", PlaceType.MUSEUM, 37.7799, -122.4158, 4.5f, true, 1, 0),
            AppPlace("childrens_creativity", "Children's Creativity Museum", PlaceType.MUSEUM, 37.7847, -122.4007, 4.4f, true, 2, 15),
            AppPlace("zeum", "Zeum Theater", PlaceType.MUSEUM, 37.7848, -122.4009, 4.3f, true, 2, 12),
            AppPlace("yerba_buena", "Yerba Buena Center for Arts", PlaceType.MUSEUM, 37.7854, -122.4020, 4.4f, true, 2, 15),
            AppPlace("museum_performance", "Museum of Performance + Design", PlaceType.MUSEUM, 37.7863, -122.4015, 4.2f, true, 1, 10),
            AppPlace("sf_arts_commission", "SF Arts Commission Gallery", PlaceType.MUSEUM, 37.7798, -122.4191, 4.3f, true, 1, 0),
            AppPlace("luggage_store", "Luggage Store Gallery", PlaceType.MUSEUM, 37.7851, -122.4080, 4.2f, true, 1, 0),
            AppPlace("catharine_clark", "Catharine Clark Gallery", PlaceType.MUSEUM, 37.7694, -122.4020, 4.4f, true, 1, 0),
            AppPlace("fraenkel_gallery", "Fraenkel Gallery", PlaceType.MUSEUM, 37.7887, -122.4018, 4.5f, true, 1, 0)
        )
    }

    suspend fun searchParks(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for parks in SF")
        return listOf(
            // Golden Gate Park Area
            AppPlace("ggpark", "Golden Gate Park", PlaceType.PARK, 37.7694, -122.4862, 4.8f, true, 0, 0),
            AppPlace("stow_lake", "Stow Lake", PlaceType.PARK, 37.7694, -122.4780, 4.7f, true, 0, 0),
            AppPlace("ggpark_polo", "Polo Fields", PlaceType.PARK, 37.7705, -122.4925, 4.5f, true, 0, 0),
            AppPlace("windmills", "Dutch Windmill Area", PlaceType.PARK, 37.7711, -122.5093, 4.6f, true, 0, 0),
            AppPlace("music_concourse", "Music Concourse", PlaceType.PARK, 37.7708, -122.4683, 4.5f, true, 0, 0),
            
            // Northern Waterfront
            AppPlace("crissy", "Crissy Field", PlaceType.PARK, 37.8050, -122.4650, 4.7f, true, 0, 0),
            AppPlace("presidio", "Presidio National Park", PlaceType.PARK, 37.7989, -122.4662, 4.8f, true, 0, 0),
            AppPlace("fort_point", "Fort Point Area", PlaceType.PARK, 37.8108, -122.4764, 4.7f, true, 0, 0),
            AppPlace("bakers_beach", "Baker Beach", PlaceType.PARK, 37.7930, -122.4836, 4.7f, true, 0, 0),
            AppPlace("marina_green", "Marina Green", PlaceType.PARK, 37.8038, -122.4388, 4.6f, true, 0, 0),
            AppPlace("fort_mason_park", "Fort Mason Gardens", PlaceType.PARK, 37.8062, -122.4313, 4.5f, true, 0, 0),
            
            // Mission / Castro
            AppPlace("dolores", "Dolores Park", PlaceType.PARK, 37.7596, -122.4269, 4.7f, true, 0, 0),
            AppPlace("mission_playground", "Mission Playground", PlaceType.PARK, 37.7543, -122.4152, 4.4f, true, 0, 0),
            AppPlace("balboa_park", "Balboa Park", PlaceType.PARK, 37.7211, -122.4450, 4.5f, true, 0, 0),
            AppPlace("glen_canyon", "Glen Canyon Park", PlaceType.PARK, 37.7419, -122.4418, 4.6f, true, 0, 0),
            AppPlace("bernal_heights", "Bernal Heights Park", PlaceType.PARK, 37.7417, -122.4197, 4.7f, true, 0, 0),
            AppPlace("holly_park", "Holly Park", PlaceType.PARK, 37.7409, -122.4221, 4.4f, true, 0, 0),
            
            // Central / Downtown
            AppPlace("alamo", "Alamo Square", PlaceType.PARK, 37.7766, -122.4345, 4.6f, true, 0, 0),
            AppPlace("buena_vista", "Buena Vista Park", PlaceType.PARK, 37.7676, -122.4403, 4.6f, true, 0, 0),
            AppPlace("corona_heights", "Corona Heights Park", PlaceType.PARK, 37.7637, -122.4380, 4.7f, true, 0, 0),
            AppPlace("lafayette_park", "Lafayette Park", PlaceType.PARK, 37.7916, -122.4285, 4.6f, true, 0, 0),
            AppPlace("alta_plaza", "Alta Plaza Park", PlaceType.PARK, 37.7919, -122.4350, 4.6f, true, 0, 0),
            AppPlace("jefferson_square", "Jefferson Square Park", PlaceType.PARK, 37.7769, -122.4237, 4.4f, true, 0, 0),
            AppPlace("civic_center", "Civic Center Plaza", PlaceType.PARK, 37.7799, -122.4193, 4.3f, true, 0, 0),
            AppPlace("un_plaza", "UN Plaza", PlaceType.PARK, 37.7802, -122.4137, 4.2f, true, 0, 0),
            AppPlace("south_park", "South Park", PlaceType.PARK, 37.7799, -122.3926, 4.5f, true, 0, 0),
            
            // Western SF
            AppPlace("landsend", "Lands End", PlaceType.PARK, 37.7849, -122.5080, 4.8f, true, 0, 0),
            AppPlace("sutro_heights", "Sutro Heights Park", PlaceType.PARK, 37.7794, -122.5126, 4.7f, true, 0, 0),
            AppPlace("ocean_beach", "Ocean Beach", PlaceType.PARK, 37.7602, -122.5110, 4.6f, true, 0, 0),
            AppPlace("lincoln_park", "Lincoln Park", PlaceType.PARK, 37.7837, -122.4980, 4.7f, true, 0, 0),
            AppPlace("fort_funston", "Fort Funston", PlaceType.PARK, 37.7134, -122.5012, 4.7f, true, 0, 0),
            
            // Bayview / Southeast
            AppPlace("candlestick", "Candlestick Point", PlaceType.PARK, 37.7098, -122.3860, 4.4f, true, 0, 0),
            AppPlace("mclaren_park", "McLaren Park", PlaceType.PARK, 37.7192, -122.4181, 4.6f, true, 0, 0),
            AppPlace("portola", "John McLaren Park", PlaceType.PARK, 37.7194, -122.4236, 4.5f, true, 0, 0),
            
            // North Beach / Telegraph Hill
            AppPlace("coit_tower", "Coit Tower Park", PlaceType.PARK, 37.8024, -122.4058, 4.7f, true, 0, 5),
            AppPlace("washington_square", "Washington Square", PlaceType.PARK, 37.8001, -122.4102, 4.5f, true, 0, 0),
            AppPlace("telegraph_hill", "Telegraph Hill Park", PlaceType.PARK, 37.8015, -122.4065, 4.6f, true, 0, 0),
            
            // Various
            AppPlace("twin_peaks", "Twin Peaks", PlaceType.PARK, 37.7544, -122.4477, 4.8f, true, 0, 0),
            AppPlace("mt_davidson", "Mount Davidson Park", PlaceType.PARK, 37.7382, -122.4550, 4.6f, true, 0, 0),
            AppPlace("lake_merced", "Lake Merced Park", PlaceType.PARK, 37.7167, -122.4871, 4.6f, true, 0, 0),
            AppPlace("stern_grove", "Stern Grove", PlaceType.PARK, 37.7290, -122.4743, 4.7f, true, 0, 0),
            AppPlace("mount_sutro", "Mount Sutro Open Space", PlaceType.PARK, 37.7538, -122.4518, 4.5f, true, 0, 0)
        )
    }

    suspend fun searchRestaurants(cuisineTypes: List<String>): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for restaurants: $cuisineTypes")
        val allRestaurants = mutableListOf<AppPlace>()

        if (cuisineTypes.contains("italian")) {
            allRestaurants.addAll(
                listOf(
                    // North Beach (Italian Hub)
                    AppPlace("flour_water", "Flour + Water", PlaceType.RESTAURANT, 37.7616, -122.4094, 4.5f, true, 2, 30),
                    AppPlace("tony_pizza", "Tony's Pizza Napoletana", PlaceType.RESTAURANT, 37.7980, -122.4094, 4.6f, true, 2, 25),
                    AppPlace("sotto_mare", "Sotto Mare", PlaceType.RESTAURANT, 37.8008, -122.4102, 4.5f, true, 2, 35),
                    AppPlace("golden_boy", "Golden Boy Pizza", PlaceType.RESTAURANT, 37.7990, -122.4080, 4.5f, true, 1, 15),
                    AppPlace("original_joes", "Original Joe's", PlaceType.RESTAURANT, 37.7991, -122.4096, 4.4f, true, 2, 30),
                    AppPlace("caffe_sport", "Caffe Sport", PlaceType.RESTAURANT, 37.8007, -122.4101, 4.3f, true, 2, 28),
                    AppPlace("mama_sf", "Mama's on Washington Square", PlaceType.RESTAURANT, 37.7999, -122.4105, 4.6f, true, 2, 20),
                    AppPlace("calzone", "Calzone's", PlaceType.RESTAURANT, 37.8004, -122.4084, 4.4f, true, 2, 22),
                    AppPlace("molinari", "Molinari Delicatessen", PlaceType.RESTAURANT, 37.7998, -122.4089, 4.7f, true, 1, 18),
                    AppPlace("liguria_bakery", "Liguria Bakery", PlaceType.RESTAURANT, 37.7987, -122.4093, 4.6f, true, 1, 12),
                    // Marina / Cow Hollow
                    AppPlace("delarosa", "Delarosa", PlaceType.RESTAURANT, 37.7989, -122.4354, 4.4f, true, 2, 25),
                    AppPlace("a16", "A16", PlaceType.RESTAURANT, 37.7999, -122.4358, 4.5f, true, 2, 32),
                    AppPlace("gaspare", "Gaspare's Pizza House", PlaceType.RESTAURANT, 37.8038, -122.4378, 4.4f, true, 2, 24),
                    // Inner Richmond
                    AppPlace("pazzia", "Pazzia Restaurant & Pizzeria", PlaceType.RESTAURANT, 37.7715, -122.4700, 4.3f, true, 2, 25),
                    AppPlace("picas", "Pica's", PlaceType.RESTAURANT, 37.7791, -122.4629, 4.4f, true, 1, 20),
                    // Mission
                    AppPlace("beretta", "Beretta", PlaceType.RESTAURANT, 37.7589, -122.4213, 4.4f, true, 2, 28),
                    AppPlace("delfina", "Delfina", PlaceType.RESTAURANT, 37.7612, -122.4179, 4.6f, true, 2, 35),
                    AppPlace("pizzeria_delfina", "Pizzeria Delfina", PlaceType.RESTAURANT, 37.7594, -122.4261, 4.5f, true, 2, 22),
                    AppPlace("locanda", "Locanda", PlaceType.RESTAURANT, 37.7590, -122.4212, 4.5f, true, 2, 30),
                    // SoMa / Downtown
                    AppPlace("perbacco", "Perbacco", PlaceType.RESTAURANT, 37.7906, -122.4024, 4.5f, true, 2, 32),
                    AppPlace("cotogna", "Cotogna", PlaceType.RESTAURANT, 37.7970, -122.4032, 4.6f, true, 2, 35),
                    AppPlace("ideale", "Ideale", PlaceType.RESTAURANT, 37.7826, -122.4098, 4.3f, true, 2, 28),
                    // Nob Hill
                    AppPlace("acquerello", "Acquerello", PlaceType.RESTAURANT, 37.7925, -122.4166, 4.7f, true, 3, 45),
                    // Russian Hill
                    AppPlace("da_flora", "Da Flora", PlaceType.RESTAURANT, 37.8036, -122.4189, 4.5f, true, 2, 30),
                    AppPlace("venticello", "Venticello", PlaceType.RESTAURANT, 37.8043, -122.4184, 4.4f, true, 2, 28),
                    // Potrero Hill
                    AppPlace("cento_osteria", "Cento Osteria", PlaceType.RESTAURANT, 37.7601, -122.4018, 4.3f, true, 2, 26),
                    // Castro
                    AppPlace("postrio", "Postrio", PlaceType.RESTAURANT, 37.7615, -122.4345, 4.3f, true, 2, 30),
                    // Hayes Valley
                    AppPlace("starbelly", "Starbelly", PlaceType.RESTAURANT, 37.7650, -122.4295, 4.4f, true, 2, 27),
                    // Outer Sunset
                    AppPlace("palinode", "Palinode", PlaceType.RESTAURANT, 37.7551, -122.4863, 4.2f, true, 2, 24),
                    AppPlace("mozzeria", "Mozzeria", PlaceType.RESTAURANT, 37.7603, -122.4643, 4.6f, true, 2, 22)
                )
            )
        }

        if (cuisineTypes.contains("mexican")) {
            allRestaurants.addAll(
                listOf(
                    // Mission District (Mexican Hub)
                    AppPlace("la_taqueria", "La Taqueria", PlaceType.RESTAURANT, 37.7508, -122.4183, 4.6f, true, 1, 15),
                    AppPlace("el_farolito", "El Farolito", PlaceType.RESTAURANT, 37.7479, -122.4176, 4.5f, true, 1, 12),
                    AppPlace("la_palma", "La Palma Mexicatessen", PlaceType.RESTAURANT, 37.7528, -122.4172, 4.5f, true, 1, 14),
                    AppPlace("taqueria_cancun", "Taqueria Cancun", PlaceType.RESTAURANT, 37.7498, -122.4192, 4.4f, true, 1, 13),
                    AppPlace("pancho_villa", "Pancho Villa Taqueria", PlaceType.RESTAURANT, 37.7514, -122.4171, 4.3f, true, 1, 14),
                    AppPlace("papalote", "Papalote Mexican Grill", PlaceType.RESTAURANT, 37.7616, -122.4252, 4.4f, true, 2, 18),
                    AppPlace("gracias_madre", "Gracias Madre", PlaceType.RESTAURANT, 37.7622, -122.4245, 4.4f, true, 2, 28),
                    AppPlace("nopalito", "Nopalito", PlaceType.RESTAURANT, 37.7695, -122.4887, 4.4f, true, 2, 25),
                    AppPlace("tacolicious", "Tacolicious Mission", PlaceType.RESTAURANT, 37.7499, -122.4191, 4.3f, true, 2, 20),
                    AppPlace("lolos", "Lolo's", PlaceType.RESTAURANT, 37.7530, -122.4179, 4.5f, true, 1, 15),
                    AppPlace("el_tonayense", "El Tonayense", PlaceType.RESTAURANT, 37.7521, -122.4173, 4.4f, true, 1, 11),
                    AppPlace("taqueria_guadalajara", "Taqueria Guadalajara", PlaceType.RESTAURANT, 37.7526, -122.4188, 4.3f, true, 1, 12),
                    AppPlace("el_buen_comer", "El Buen Comer", PlaceType.RESTAURANT, 37.7474, -122.4172, 4.5f, true, 1, 13),
                    AppPlace("taqueria_vallarta", "Taqueria Vallarta", PlaceType.RESTAURANT, 37.7502, -122.4180, 4.4f, true, 1, 12),
                    AppPlace("la_victoria", "La Victoria", PlaceType.RESTAURANT, 37.7515, -122.4178, 4.3f, true, 1, 11),
                    // Marina
                    AppPlace("tacolicious_marina", "Tacolicious Marina", PlaceType.RESTAURANT, 37.8025, -122.4352, 4.4f, true, 2, 22),
                    AppPlace("tacko", "Tacko", PlaceType.RESTAURANT, 37.7999, -122.4355, 4.3f, true, 2, 18),
                    // Castro
                    AppPlace("la_mediterranee", "La Mediterranee", PlaceType.RESTAURANT, 37.7615, -122.4347, 4.4f, true, 2, 20),
                    // Inner Sunset
                    AppPlace("nopalito_9th", "Nopalito 9th Avenue", PlaceType.RESTAURANT, 37.7639, -122.4660, 4.4f, true, 2, 24),
                    // Hayes Valley
                    AppPlace("panchitas", "Panchita's", PlaceType.RESTAURANT, 37.7756, -122.4244, 4.3f, true, 1, 16),
                    // North Beach
                    AppPlace("mamacitas", "Mamacita's", PlaceType.RESTAURANT, 37.8012, -122.4345, 4.3f, true, 2, 22),
                    // SoMa
                    AppPlace("tropisueno", "Tropisueno", PlaceType.RESTAURANT, 37.7786, -122.4179, 4.4f, true, 2, 19),
                    // Potrero Hill
                    AppPlace("chez_maman", "Chez Maman", PlaceType.RESTAURANT, 37.7618, -122.4009, 4.5f, true, 2, 20),
                    // Outer Mission
                    AppPlace("los_panchos", "Los Panchos", PlaceType.RESTAURANT, 37.7265, -122.4221, 4.4f, true, 1, 13),
                    // Bernal Heights
                    AppPlace("el_zocalo", "El Zocalo", PlaceType.RESTAURANT, 37.7390, -122.4214, 4.3f, true, 1, 15),
                    // Financial District
                    AppPlace("colibri", "Colibri Mexican Bistro", PlaceType.RESTAURANT, 37.7902, -122.4022, 4.4f, true, 2, 22),
                    // Dogpatch
                    AppPlace("mosto", "Mosto", PlaceType.RESTAURANT, 37.7589, -122.3914, 4.3f, true, 2, 24),
                    // Noe Valley
                    AppPlace("panchitas_noe", "Panchita's Noe", PlaceType.RESTAURANT, 37.7508, -122.4314, 4.3f, true, 1, 14),
                    // Excelsior
                    AppPlace("el_rincon_yucateco", "El Rincon Yucateco", PlaceType.RESTAURANT, 37.7248, -122.4289, 4.5f, true, 1, 14),
                    // Inner Richmond
                    AppPlace("gordo", "Gordo Taqueria", PlaceType.RESTAURANT, 37.7814, -122.4614, 4.3f, true, 1, 13)
                )
            )
        }

        if (cuisineTypes.contains("american")) {
            allRestaurants.addAll(
                listOf(
                    // Downtown / Hayes Valley
                    AppPlace("zuni", "Zuni Café", PlaceType.RESTAURANT, 37.7750, -122.4223, 4.5f, true, 2, 30),
                    AppPlace("nopa", "NOPA", PlaceType.RESTAURANT, 37.7749, -122.4375, 4.4f, true, 2, 28),
                    AppPlace("jardiniere", "Jardiniere", PlaceType.RESTAURANT, 37.7773, -122.4221, 4.5f, true, 3, 40),
                    AppPlace("absinthe", "Absinthe Brasserie", PlaceType.RESTAURANT, 37.7760, -122.4237, 4.4f, true, 2, 32),
                    AppPlace("rich_table", "Rich Table", PlaceType.RESTAURANT, 37.7765, -122.4229, 4.6f, true, 3, 45),
                    // Mission / Castro
                    AppPlace("foreign_cinema", "Foreign Cinema", PlaceType.RESTAURANT, 37.7540, -122.4191, 4.5f, true, 2, 35),
                    AppPlace("lazy_bear", "Lazy Bear", PlaceType.RESTAURANT, 37.7574, -122.4211, 4.6f, true, 3, 50),
                    AppPlace("bar_tartine", "Bar Tartine", PlaceType.RESTAURANT, 37.7562, -122.4217, 4.4f, true, 2, 30),
                    AppPlace("tartine_manufactory", "Tartine Manufactory", PlaceType.RESTAURANT, 37.7597, -122.4117, 4.5f, true, 2, 25),
                    AppPlace("frances", "Frances", PlaceType.RESTAURANT, 37.7610, -122.4350, 4.6f, true, 2, 32),
                    // Outer Sunset
                    AppPlace("outerlands", "Outerlands", PlaceType.RESTAURANT, 37.7609, -122.5096, 4.5f, true, 2, 25),
                    AppPlace("trouble_coffee", "Trouble Coffee", PlaceType.RESTAURANT, 37.7604, -122.5110, 4.6f, true, 1, 12),
                    AppPlace("devils_teeth", "Devil's Teeth Baking", PlaceType.RESTAURANT, 37.7606, -122.5094, 4.7f, true, 1, 15),
                    // Marina / Cow Hollow
                    AppPlace("rose_pistola", "Rose's Cafe", PlaceType.RESTAURANT, 37.7995, -122.4308, 4.5f, true, 2, 28),
                    AppPlace("atelier_crenn", "Atelier Crenn", PlaceType.RESTAURANT, 37.7998, -122.4363, 4.8f, true, 4, 80),
                    AppPlace("greens", "Greens Restaurant", PlaceType.RESTAURANT, 37.8055, -122.4323, 4.3f, true, 2, 25),
                    // North Beach
                    AppPlace("north_beach_restaurant", "North Beach Restaurant", PlaceType.RESTAURANT, 37.8011, -122.4091, 4.4f, true, 2, 30),
                    // Financial District
                    AppPlace("boulevard", "Boulevard", PlaceType.RESTAURANT, 37.7952, -122.3939, 4.5f, true, 3, 42),
                    AppPlace("quince", "Quince", PlaceType.RESTAURANT, 37.7942, -122.4021, 4.7f, true, 4, 75),
                    AppPlace("benu", "Benu", PlaceType.RESTAURANT, 37.7783, -122.3954, 4.6f, true, 4, 70),
                    // SoMa
                    AppPlace("marlowe", "Marlowe", PlaceType.RESTAURANT, 37.7792, -122.4008, 4.4f, true, 2, 28),
                    AppPlace("farmtable", "Farmtable", PlaceType.RESTAURANT, 37.7821, -122.4053, 4.3f, true, 2, 24),
                    AppPlace("sentinel", "The Sentinel", PlaceType.RESTAURANT, 37.7875, -122.3995, 4.5f, true, 1, 16),
                    // Potrero Hill
                    AppPlace("chez_papa_bistrot", "Chez Papa Bistrot", PlaceType.RESTAURANT, 37.7596, -122.4011, 4.4f, true, 2, 26),
                    // Richmond
                    AppPlace("aziza", "Aziza", PlaceType.RESTAURANT, 37.7793, -122.4696, 4.6f, true, 3, 38),
                    AppPlace("burma_superstar", "Burma Superstar", PlaceType.RESTAURANT, 37.7808, -122.4619, 4.5f, true, 2, 22),
                    // Nob Hill
                    AppPlace("swan_oyster", "Swan Oyster Depot", PlaceType.RESTAURANT, 37.7921, -122.4202, 4.6f, true, 2, 30),
                    // Russian Hill
                    AppPlace("seven_hills", "Seven Hills", PlaceType.RESTAURANT, 37.8042, -122.4180, 4.4f, true, 2, 32),
                    // Dogpatch
                    AppPlace("piccino", "Piccino", PlaceType.RESTAURANT, 37.7602, -122.3921, 4.5f, true, 2, 22),
                    AppPlace("serpentine", "Serpentine", PlaceType.RESTAURANT, 37.7607, -122.3937, 4.3f, true, 2, 24)
                )
            )
        }

        if (cuisineTypes.contains("asian")) {
            allRestaurants.addAll(
                listOf(
                    // Chinatown
                    AppPlace("dragon_beaux", "Dragon Beaux", PlaceType.RESTAURANT, 37.7943, -122.4078, 4.4f, true, 2, 25),
                    AppPlace("r&g_lounge", "R&G Lounge", PlaceType.RESTAURANT, 37.7949, -122.4061, 4.3f, true, 2, 28),
                    AppPlace("z&y", "Z & Y", PlaceType.RESTAURANT, 37.7964, -122.4069, 4.5f, true, 2, 24),
                    AppPlace("koi_palace", "Koi Palace", PlaceType.RESTAURANT, 37.7944, -122.4072, 4.4f, true, 2, 26),
                    AppPlace("house_nanking", "House of Nanking", PlaceType.RESTAURANT, 37.7978, -122.4059, 4.3f, true, 1, 18),
                    AppPlace("yank_sing", "Yank Sing", PlaceType.RESTAURANT, 37.7901, -122.3972, 4.5f, true, 2, 30),
                    AppPlace("lai_hong_lounge", "Lai Hong Lounge", PlaceType.RESTAURANT, 37.7955, -122.4067, 4.3f, true, 2, 22),
                    AppPlace("great_eastern", "Great Eastern", PlaceType.RESTAURANT, 37.7952, -122.4076, 4.3f, true, 2, 24),
                    AppPlace("hakkasan", "Hakkasan", PlaceType.RESTAURANT, 37.7887, -122.3998, 4.4f, true, 3, 45),
                    AppPlace("golden_flower", "Golden Flower", PlaceType.RESTAURANT, 37.7956, -122.4068, 4.2f, true, 2, 23),
                    // Japantown
                    AppPlace("mensho_tokyo", "Mensho Tokyo", PlaceType.RESTAURANT, 37.7848, -122.4305, 4.5f, true, 1, 18),
                    AppPlace("hinodeya", "Hinodeya", PlaceType.RESTAURANT, 37.7850, -122.4303, 4.4f, true, 1, 16),
                    AppPlace("waraku", "Waraku", PlaceType.RESTAURANT, 37.7853, -122.4307, 4.3f, true, 2, 22),
                    AppPlace("benkyodo", "Benkyodo", PlaceType.RESTAURANT, 37.7851, -122.4304, 4.5f, true, 1, 12),
                    AppPlace("izakaya_yuzuki", "Izakaya Yuzuki", PlaceType.RESTAURANT, 37.7854, -122.4309, 4.4f, true, 2, 24),
                    // Mission
                    AppPlace("rintaro", "Rintaro", PlaceType.RESTAURANT, 37.7600, -122.4194, 4.5f, true, 2, 30),
                    AppPlace("burma_love", "Burma Love", PlaceType.RESTAURANT, 37.7602, -122.4199, 4.4f, true, 2, 24),
                    AppPlace("ramen_yamadaya", "Ramen Yamadaya", PlaceType.RESTAURANT, 37.7495, -122.4187, 4.4f, true, 1, 16),
                    // Richmond
                    AppPlace("ton_kiang", "Ton Kiang", PlaceType.RESTAURANT, 37.7815, -122.4606, 4.4f, true, 2, 26),
                    AppPlace("thanh_long", "Thanh Long", PlaceType.RESTAURANT, 37.7760, -122.4937, 4.5f, true, 2, 35),
                    AppPlace("chapeau", "Chapeau!", PlaceType.RESTAURANT, 37.7802, -122.4632, 4.6f, true, 2, 32),
                    AppPlace("dragon_well", "Dragon Well", PlaceType.RESTAURANT, 37.7811, -122.4620, 4.3f, true, 2, 22),
                    AppPlace("koo", "Koo", PlaceType.RESTAURANT, 37.7778, -122.4678, 4.4f, true, 2, 24),
                    // Inner Sunset
                    AppPlace("ebisu", "Ebisu", PlaceType.RESTAURANT, 37.7640, -122.4681, 4.5f, true, 2, 28),
                    AppPlace("izakaya_sozai", "Izakaya Sozai", PlaceType.RESTAURANT, 37.7634, -122.4674, 4.4f, true, 1, 20),
                    AppPlace("sushi_toni", "Sushi Toni", PlaceType.RESTAURANT, 37.7623, -122.4653, 4.3f, true, 2, 25),
                    // SoMa
                    AppPlace("okaeri", "Okaeri", PlaceType.RESTAURANT, 37.7793, -122.4012, 4.4f, true, 2, 26),
                    AppPlace("ramen_underground", "Ramen Underground", PlaceType.RESTAURANT, 37.7848, -122.4009, 4.3f, true, 1, 14),
                    // Hayes Valley
                    AppPlace("souvla", "Souvla", PlaceType.RESTAURANT, 37.7756, -122.4248, 4.5f, true, 1, 16),
                    AppPlace("namu_gaji", "Namu Gaji", PlaceType.RESTAURANT, 37.7763, -122.4244, 4.4f, true, 2, 22)
                )
            )
        }

        if (cuisineTypes.contains("seafood")) {
            allRestaurants.addAll(
                listOf(
                    // Fisherman's Wharf
                    AppPlace("scomas", "Scoma's", PlaceType.RESTAURANT, 37.8095, -122.4185, 4.3f, true, 3, 45),
                    AppPlace("alioto", "Alioto's", PlaceType.RESTAURANT, 37.8087, -122.4180, 4.2f, true, 3, 42),
                    AppPlace("franciscan", "Franciscan Crab Restaurant", PlaceType.RESTAURANT, 37.8082, -122.4174, 4.3f, true, 3, 40),
                    AppPlace("crab_house", "Crab House at Pier 39", PlaceType.RESTAURANT, 37.8087, -122.4098, 4.2f, true, 3, 38),
                    AppPlace("fog_harbor", "Fog Harbor Fish House", PlaceType.RESTAURANT, 37.8089, -122.4103, 4.3f, true, 3, 35),
                    AppPlace("pier_market", "Pier Market Seafood", PlaceType.RESTAURANT, 37.8084, -122.4175, 4.2f, true, 3, 36),
                    // Nob Hill / Russian Hill
                    AppPlace("swan_oyster", "Swan Oyster Depot", PlaceType.RESTAURANT, 37.7921, -122.4202, 4.6f, true, 2, 30),
                    AppPlace("seven_seas", "Seven Seas", PlaceType.RESTAURANT, 37.8011, -122.4172, 4.3f, true, 2, 28),
                    // Financial District / Embarcadero
                    AppPlace("tadich_grill", "Tadich Grill", PlaceType.RESTAURANT, 37.7941, -122.3988, 4.4f, true, 2, 32),
                    AppPlace("waterbar", "Waterbar", PlaceType.RESTAURANT, 37.7927, -122.3897, 4.4f, true, 3, 42),
                    AppPlace("hog_island", "Hog Island Oyster Co", PlaceType.RESTAURANT, 37.7956, -122.3935, 4.5f, true, 2, 30),
                    AppPlace("anchor_oyster", "Anchor Oyster Bar", PlaceType.RESTAURANT, 37.7610, -122.4353, 4.5f, true, 2, 28),
                    AppPlace("farallon", "Farallon", PlaceType.RESTAURANT, 37.7894, -122.4065, 4.4f, true, 3, 45),
                    AppPlace("bar_crudo", "Bar Crudo", PlaceType.RESTAURANT, 37.7738, -122.4373, 4.4f, true, 2, 32),
                    // Marina
                    AppPlace("blue_mermaid", "Blue Mermaid", PlaceType.RESTAURANT, 37.8084, -122.4162, 4.2f, true, 2, 26),
                    AppPlace("a16_marina", "A16", PlaceType.RESTAURANT, 37.7999, -122.4358, 4.5f, true, 2, 32),
                    // Outer Richmond
                    AppPlace("thanh_long_richmond", "Thanh Long", PlaceType.RESTAURANT, 37.7760, -122.4937, 4.5f, true, 2, 35),
                    AppPlace("ton_kiang_richmond", "Ton Kiang", PlaceType.RESTAURANT, 37.7815, -122.4606, 4.4f, true, 2, 26),
                    // Potrero Hill
                    AppPlace("chez_maman_potrero", "Chez Maman", PlaceType.RESTAURANT, 37.7618, -122.4009, 4.5f, true, 2, 24),
                    // Dogpatch
                    AppPlace("serpentine_dogpatch", "Serpentine", PlaceType.RESTAURANT, 37.7607, -122.3937, 4.3f, true, 2, 24),
                    AppPlace("the_ramp", "The Ramp", PlaceType.RESTAURANT, 37.7539, -122.3878, 4.3f, true, 2, 22),
                    // SoMa
                    AppPlace("yank_sing_soma", "Yank Sing Rincon", PlaceType.RESTAURANT, 37.7901, -122.3972, 4.5f, true, 2, 30),
                    // Hayes Valley
                    AppPlace("absinthe_hayes", "Absinthe Brasserie", PlaceType.RESTAURANT, 37.7760, -122.4237, 4.4f, true, 2, 32),
                    // Castro
                    AppPlace("anchor_castro", "Anchor Oyster Bar Castro", PlaceType.RESTAURANT, 37.7610, -122.4353, 4.5f, true, 2, 28),
                    // Inner Sunset
                    AppPlace("ebisu_sunset", "Ebisu", PlaceType.RESTAURANT, 37.7640, -122.4681, 4.5f, true, 2, 28),
                    // Mission
                    AppPlace("locanda_mission", "Locanda", PlaceType.RESTAURANT, 37.7590, -122.4212, 4.5f, true, 2, 30),
                    // Pacific Heights
                    AppPlace("spruce", "Spruce", PlaceType.RESTAURANT, 37.7886, -122.4287, 4.5f, true, 3, 40),
                    // North Beach
                    AppPlace("sotto_mare_nb", "Sotto Mare", PlaceType.RESTAURANT, 37.8008, -122.4102, 4.5f, true, 2, 35),
                    // Noe Valley
                    AppPlace("incanto", "Incanto", PlaceType.RESTAURANT, 37.7408, -122.4291, 4.4f, true, 2, 32),
                    // Lower Haight
                    AppPlace("thep_phanom", "Thep Phanom", PlaceType.RESTAURANT, 37.7732, -122.4296, 4.4f, true, 2, 26)
                )
            )
        }

        if (cuisineTypes.contains("vegetarian")) {
            allRestaurants.addAll(
                listOf(
                    // Marina / Fort Mason
                    AppPlace("greens", "Greens Restaurant", PlaceType.RESTAURANT, 37.8055, -122.4323, 4.3f, true, 2, 25),
                    // Mission
                    AppPlace("gracias_madre", "Gracias Madre", PlaceType.RESTAURANT, 37.7622, -122.4245, 4.4f, true, 2, 28),
                    AppPlace("millennium", "Millennium", PlaceType.RESTAURANT, 37.7878, -122.4097, 4.5f, true, 2, 30),
                    AppPlace("shizen", "Shizen Vegan Sushi", PlaceType.RESTAURANT, 37.7596, -122.4202, 4.6f, true, 2, 26),
                    AppPlace("encuentro", "Encuentro", PlaceType.RESTAURANT, 37.7610, -122.4249, 4.3f, true, 2, 22),
                    // Hayes Valley
                    AppPlace("namu_gaji_hayes", "Namu Gaji", PlaceType.RESTAURANT, 37.7763, -122.4244, 4.4f, true, 2, 22),
                    AppPlace("souvla_hayes", "Souvla", PlaceType.RESTAURANT, 37.7756, -122.4248, 4.5f, true, 1, 16),
                    // Inner Sunset
                    AppPlace("outerlands_sunset", "Outerlands", PlaceType.RESTAURANT, 37.7609, -122.5096, 4.5f, true, 2, 25),
                    AppPlace("zazie", "Zazie", PlaceType.RESTAURANT, 37.7650, -122.4479, 4.5f, true, 2, 24),
                    AppPlace("arizmendi", "Arizmendi Bakery", PlaceType.RESTAURANT, 37.7642, -122.4660, 4.6f, true, 1, 12),
                    // Richmond
                    AppPlace("burma_superstar_richmond", "Burma Superstar", PlaceType.RESTAURANT, 37.7808, -122.4619, 4.5f, true, 2, 22),
                    AppPlace("yamo", "Yamo", PlaceType.RESTAURANT, 37.7816, -122.4612, 4.4f, true, 1, 14),
                    // Financial District
                    AppPlace("source", "The Source", PlaceType.RESTAURANT, 37.7878, -122.4097, 4.3f, true, 2, 20),
                    // SoMa
                    AppPlace("loving_hut", "Loving Hut", PlaceType.RESTAURANT, 37.7815, -122.4103, 4.2f, true, 1, 15),
                    AppPlace("plant", "PLANT Cafe Organic", PlaceType.RESTAURANT, 37.7896, -122.3959, 4.3f, true, 2, 18),
                    // Castro
                    AppPlace("herbivore", "Herbivore", PlaceType.RESTAURANT, 37.7615, -122.4347, 4.3f, true, 1, 16),
                    // Potrero Hill
                    AppPlace("chez_maman_potrero_veg", "Chez Maman", PlaceType.RESTAURANT, 37.7618, -122.4009, 4.5f, true, 2, 24),
                    // Noe Valley
                    AppPlace("firefly", "Firefly", PlaceType.RESTAURANT, 37.7483, -122.4314, 4.4f, true, 2, 26),
                    // Haight-Ashbury
                    AppPlace("cha_ya", "Cha-Ya Vegetarian", PlaceType.RESTAURANT, 37.7695, -122.4478, 4.5f, true, 2, 20),
                    AppPlace("golden_era", "Golden Era Vegan", PlaceType.RESTAURANT, 37.7718, -122.4341, 4.3f, true, 1, 14),
                    // Tenderloin
                    AppPlace("saigon_sandwich", "Saigon Sandwich", PlaceType.RESTAURANT, 37.7841, -122.4112, 4.6f, true, 1, 10),
                    // Inner Richmond
                    AppPlace("shangri_la", "Shangri-La Vegetarian", PlaceType.RESTAURANT, 37.7804, -122.4639, 4.4f, true, 1, 16),
                    // Japantown
                    AppPlace("waraku_veg", "Waraku", PlaceType.RESTAURANT, 37.7853, -122.4307, 4.3f, true, 2, 22),
                    // Excelsior
                    AppPlace("vegan_picnic", "Vegan Picnic", PlaceType.RESTAURANT, 37.7243, -122.4300, 4.5f, true, 1, 14),
                    // Dogpatch
                    AppPlace("piccino_veg", "Piccino", PlaceType.RESTAURANT, 37.7602, -122.3921, 4.5f, true, 2, 22),
                    // Lower Haight
                    AppPlace("rosamunde", "Rosamunde Sausage Grill", PlaceType.RESTAURANT, 37.7723, -122.4287, 4.4f, true, 1, 14),
                    // North Beach
                    AppPlace("mama_sf_veg", "Mama's on Washington Square", PlaceType.RESTAURANT, 37.7999, -122.4105, 4.6f, true, 2, 20),
                    // Marina
                    AppPlace("greens_to_go", "Greens To Go", PlaceType.RESTAURANT, 37.8056, -122.4322, 4.4f, true, 1, 15),
                    // Bernal Heights
                    AppPlace("liberty_cafe", "Liberty Cafe", PlaceType.RESTAURANT, 37.7397, -122.4186, 4.5f, true, 2, 22),
                    // Glen Park
                    AppPlace("gialina", "Gialina", PlaceType.RESTAURANT, 37.7380, -122.4338, 4.5f, true, 2, 24)
                )
            )
        }

        return allRestaurants
    }

    suspend fun searchWaterfront(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for waterfront locations in SF")
        return listOf(
            // Northern Waterfront
            AppPlace("fishermans_wharf", "Fisherman's Wharf", PlaceType.WATERFRONT, 37.8080, -122.4177, 4.4f, true, 0, 0),
            AppPlace("pier39", "Pier 39", PlaceType.WATERFRONT, 37.8087, -122.4098, 4.3f, true, 0, 0),
            AppPlace("ghirardelli_square", "Ghirardelli Square", PlaceType.WATERFRONT, 37.8058, -122.4227, 4.4f, true, 0, 0),
            AppPlace("aquatic_park", "Aquatic Park", PlaceType.WATERFRONT, 37.8086, -122.4237, 4.6f, true, 0, 0),
            AppPlace("maritime_historic", "SF Maritime National Historic Park", PlaceType.WATERFRONT, 37.8087, -122.4230, 4.5f, true, 0, 0),
            AppPlace("hyde_street_pier", "Hyde Street Pier", PlaceType.WATERFRONT, 37.8089, -122.4217, 4.5f, true, 0, 5),
            AppPlace("fort_mason_pier", "Fort Mason Piers", PlaceType.WATERFRONT, 37.8067, -122.4304, 4.4f, true, 0, 0),
            AppPlace("marina_harbor", "Marina Harbor", PlaceType.WATERFRONT, 37.8049, -122.4397, 4.5f, true, 0, 0),
            AppPlace("wave_organ", "Wave Organ", PlaceType.WATERFRONT, 37.8068, -122.4366, 4.6f, true, 0, 0),
            AppPlace("yacht_harbor", "SF Yacht Harbor", PlaceType.WATERFRONT, 37.8051, -122.4385, 4.4f, true, 0, 0),
            // Eastern Waterfront
            AppPlace("embarcadero", "Embarcadero", PlaceType.WATERFRONT, 37.7955, -122.3937, 4.5f, true, 0, 0),
            AppPlace("ferry_building", "Ferry Building", PlaceType.WATERFRONT, 37.7956, -122.3935, 4.6f, true, 0, 0),
            AppPlace("rincon_park", "Rincon Park", PlaceType.WATERFRONT, 37.7917, -122.3886, 4.5f, true, 0, 0),
            AppPlace("cupids_span", "Cupid's Span", PlaceType.WATERFRONT, 37.7917, -122.3898, 4.3f, true, 0, 0),
            AppPlace("pier7", "Pier 7", PlaceType.WATERFRONT, 37.8004, -122.3972, 4.5f, true, 0, 0),
            AppPlace("pier14", "Pier 14", PlaceType.WATERFRONT, 37.7944, -122.3914, 4.6f, true, 0, 0),
            AppPlace("pier15", "Pier 15 (Exploratorium)", PlaceType.WATERFRONT, 37.8014, -122.3975, 4.5f, true, 0, 0),
            AppPlace("pier24", "Pier 24 Photography", PlaceType.WATERFRONT, 37.7887, -122.3897, 4.4f, true, 0, 0),
            // Southern Waterfront
            AppPlace("mission_bay", "Mission Bay Park", PlaceType.WATERFRONT, 37.7706, -122.3911, 4.4f, true, 0, 0),
            AppPlace("oracle_park_waterfront", "Oracle Park Waterfront", PlaceType.WATERFRONT, 37.7785, -122.3893, 4.6f, true, 0, 0),
            AppPlace("mission_creek", "Mission Creek Park", PlaceType.WATERFRONT, 37.7674, -122.3937, 4.3f, true, 0, 0),
            AppPlace("warm_water_cove", "Warm Water Cove", PlaceType.WATERFRONT, 37.7445, -122.3741, 4.4f, true, 0, 0),
            AppPlace("heron_head", "Heron's Head Park", PlaceType.WATERFRONT, 37.7276, -122.3744, 4.5f, true, 0, 0),
            AppPlace("india_basin", "India Basin Shoreline Park", PlaceType.WATERFRONT, 37.7261, -122.3767, 4.3f, true, 0, 0),
            AppPlace("hunters_point", "Hunters Point Shoreline", PlaceType.WATERFRONT, 37.7298, -122.3698, 4.2f, true, 0, 0),
            // Western Waterfront
            AppPlace("china_beach", "China Beach", PlaceType.WATERFRONT, 37.7900, -122.4902, 4.6f, true, 0, 0),
            AppPlace("lands_end_trail", "Lands End Coastal Trail", PlaceType.WATERFRONT, 37.7849, -122.5080, 4.8f, true, 0, 0),
            AppPlace("cliff_house_view", "Cliff House Viewpoint", PlaceType.WATERFRONT, 37.7783, -122.5139, 4.5f, true, 0, 0),
            AppPlace("ocean_beach_north", "Ocean Beach North", PlaceType.WATERFRONT, 37.7651, -122.5104, 4.6f, true, 0, 0),
            AppPlace("ocean_beach_south", "Ocean Beach South", PlaceType.WATERFRONT, 37.7350, -122.5110, 4.5f, true, 0, 0)
        )
    }

    suspend fun searchHistoricSites(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for historic sites in SF")
        return listOf(
            // Mission District
            AppPlace("mission_dolores", "Mission Dolores", PlaceType.HISTORIC_SITE, 37.7637, -122.4268, 4.4f, true, 1, 10),
            AppPlace("mission_dolores_park", "Mission Dolores Basilica", PlaceType.HISTORIC_SITE, 37.7639, -122.4267, 4.5f, true, 1, 5),
            // Nob Hill / Downtown
            AppPlace("cable_car_museum", "Cable Car Museum", PlaceType.HISTORIC_SITE, 37.7947, -122.4114, 4.6f, true, 1, 15),
            AppPlace("grace_cathedral", "Grace Cathedral", PlaceType.HISTORIC_SITE, 37.7918, -122.4125, 4.6f, true, 1, 5),
            AppPlace("fairmont", "Fairmont Hotel (Historic)", PlaceType.HISTORIC_SITE, 37.7923, -122.4104, 4.5f, true, 1, 0),
            AppPlace("huntington_park", "Huntington Park", PlaceType.HISTORIC_SITE, 37.7922, -122.4120, 4.5f, true, 0, 0),
            // Presidio
            AppPlace("presidio", "Presidio of San Francisco", PlaceType.HISTORIC_SITE, 37.7989, -122.4662, 4.7f, true, 0, 0),
            AppPlace("fort_point", "Fort Point National Historic Site", PlaceType.HISTORIC_SITE, 37.8108, -122.4764, 4.7f, true, 0, 5),
            AppPlace("presidio_officers_club", "Presidio Officers' Club", PlaceType.HISTORIC_SITE, 37.7991, -122.4665, 4.4f, true, 1, 5),
            AppPlace("pet_cemetery", "Presidio Pet Cemetery", PlaceType.HISTORIC_SITE, 37.7983, -122.4671, 4.5f, true, 0, 0),
            AppPlace("batteries_to_bluffs", "Batteries to Bluffs Trail", PlaceType.HISTORIC_SITE, 37.7975, -122.4814, 4.6f, true, 0, 0),
            // Telegraph Hill / North Beach
            AppPlace("coit_tower", "Coit Tower", PlaceType.HISTORIC_SITE, 37.8024, -122.4058, 4.7f, true, 1, 10),
            AppPlace("telegraph_hill_stairs", "Filbert Steps", PlaceType.HISTORIC_SITE, 37.8018, -122.4064, 4.7f, true, 0, 0),
            AppPlace("saints_peter_paul", "Sts Peter & Paul Church", PlaceType.HISTORIC_SITE, 37.8003, -122.4107, 4.6f, true, 0, 0),
            // Financial District
            AppPlace("transamerica", "Transamerica Pyramid", PlaceType.HISTORIC_SITE, 37.7952, -122.4028, 4.5f, true, 0, 0),
            AppPlace("old_mint", "Old San Francisco Mint", PlaceType.HISTORIC_SITE, 37.7791, -122.4063, 4.3f, true, 1, 10),
            AppPlace("ferry_building_historic", "Ferry Building (Historic)", PlaceType.HISTORIC_SITE, 37.7956, -122.3935, 4.6f, true, 0, 0),
            AppPlace("jackson_square", "Jackson Square Historic District", PlaceType.HISTORIC_SITE, 37.7965, -122.4019, 4.4f, true, 0, 0),
            // Civic Center
            AppPlace("city_hall", "San Francisco City Hall", PlaceType.HISTORIC_SITE, 37.7793, -122.4193, 4.7f, true, 1, 10),
            AppPlace("war_memorial", "War Memorial Opera House", PlaceType.HISTORIC_SITE, 37.7788, -122.4213, 4.5f, true, 1, 15),
            AppPlace("sf_main_library", "SF Main Library", PlaceType.HISTORIC_SITE, 37.7799, -122.4158, 4.5f, true, 0, 0),
            AppPlace("asian_art_museum_building", "Asian Art Museum Building", PlaceType.HISTORIC_SITE, 37.7803, -122.4158, 4.5f, true, 0, 0),
            // Pacific Heights / Marina
            AppPlace("palace_fine_arts", "Palace of Fine Arts", PlaceType.HISTORIC_SITE, 37.8033, -122.4477, 4.7f, true, 0, 0),
            AppPlace("haas_lilienthal", "Haas-Lilienthal House", PlaceType.HISTORIC_SITE, 37.7912, -122.4253, 4.5f, true, 1, 15),
            AppPlace("flood_mansion", "Flood Mansion", PlaceType.HISTORIC_SITE, 37.7926, -122.4280, 4.3f, true, 0, 0),
            AppPlace("octagon_house", "Octagon House", PlaceType.HISTORIC_SITE, 37.8004, -122.4309, 4.3f, true, 1, 10),
            // Richmond / Sunset
            AppPlace("sutro_baths", "Sutro Baths Ruins", PlaceType.HISTORIC_SITE, 37.7805, -122.5135, 4.6f, true, 0, 0),
            AppPlace("cliff_house", "Cliff House", PlaceType.HISTORIC_SITE, 37.7783, -122.5139, 4.4f, true, 0, 0),
            AppPlace("legion_honor_building", "Legion of Honor Building", PlaceType.HISTORIC_SITE, 37.7849, -122.5001, 4.6f, true, 0, 0),
            // Castro
            AppPlace("castro_theatre", "Castro Theatre", PlaceType.HISTORIC_SITE, 37.7621, -122.4349, 4.6f, true, 1, 12),
            AppPlace("harvey_milk_plaza", "Harvey Milk Plaza", PlaceType.HISTORIC_SITE, 37.7620, -122.4348, 4.5f, true, 0, 0),
            // Golden Gate Park
            AppPlace("conservatory_flowers", "Conservatory of Flowers", PlaceType.HISTORIC_SITE, 37.7727, -122.4608, 4.6f, true, 1, 10),
            AppPlace("dutch_windmill", "Dutch Windmill", PlaceType.HISTORIC_SITE, 37.7711, -122.5093, 4.5f, true, 0, 0),
            AppPlace("golden_gate_park_carousel", "Golden Gate Park Carousel", PlaceType.HISTORIC_SITE, 37.7700, -122.4734, 4.5f, true, 1, 5),
            // Chinatown
            AppPlace("chinatown_gate", "Chinatown Gate", PlaceType.HISTORIC_SITE, 37.7901, -122.4056, 4.5f, true, 0, 0),
            AppPlace("tin_how_temple", "Tin How Temple", PlaceType.HISTORIC_SITE, 37.7956, -122.4066, 4.4f, true, 0, 0),
            // Various
            AppPlace("golden_gate_bridge", "Golden Gate Bridge", PlaceType.HISTORIC_SITE, 37.8199, -122.4783, 4.8f, true, 0, 0),
            AppPlace("alcatraz", "Alcatraz Island", PlaceType.HISTORIC_SITE, 37.8270, -122.4230, 4.7f, true, 3, 40),
            AppPlace("angel_island", "Angel Island State Park", PlaceType.HISTORIC_SITE, 37.8619, -122.4326, 4.7f, true, 2, 20)
        )
    }

    suspend fun searchShopping(): List<AppPlace> {
        Log.d("PlacesRepository", "Searching for shopping areas in SF")
        return listOf(
            // Downtown / Union Square
            AppPlace("union_square", "Union Square", PlaceType.SHOPPING, 37.7879, -122.4075, 4.3f, true, 0, 0),
            AppPlace("westfield", "Westfield San Francisco Centre", PlaceType.SHOPPING, 37.7845, -122.4062, 4.3f, true, 0, 0),
            AppPlace("macys_sf", "Macy's Union Square", PlaceType.SHOPPING, 37.7875, -122.4073, 4.2f, true, 0, 0),
            AppPlace("saks_fifth", "Saks Fifth Avenue", PlaceType.SHOPPING, 37.7888, -122.4069, 4.3f, true, 0, 0),
            AppPlace("neiman_marcus", "Neiman Marcus", PlaceType.SHOPPING, 37.7883, -122.4076, 4.2f, true, 0, 0),
            // Embarcadero / Financial District
            AppPlace("ferry_building", "Ferry Building Marketplace", PlaceType.SHOPPING, 37.7956, -122.3935, 4.6f, true, 0, 0),
            AppPlace("embarcadero_center", "Embarcadero Center", PlaceType.SHOPPING, 37.7950, -122.3985, 4.3f, true, 0, 0),
            AppPlace("crocker_galleria", "Crocker Galleria", PlaceType.SHOPPING, 37.7896, -122.4023, 4.2f, true, 0, 0),
            // Fisherman's Wharf
            AppPlace("ghirardelli", "Ghirardelli Square", PlaceType.SHOPPING, 37.8058, -122.4227, 4.4f, true, 0, 0),
            AppPlace("pier39_shops", "Pier 39 Shops", PlaceType.SHOPPING, 37.8087, -122.4098, 4.2f, true, 0, 0),
            AppPlace("anchorage", "The Anchorage", PlaceType.SHOPPING, 37.8074, -122.4190, 4.1f, true, 0, 0),
            AppPlace("cannery", "The Cannery", PlaceType.SHOPPING, 37.8064, -122.4205, 4.2f, true, 0, 0),
            // Hayes Valley
            AppPlace("hayes_valley", "Hayes Valley Shopping District", PlaceType.SHOPPING, 37.7760, -122.4240, 4.5f, true, 0, 0),
            AppPlace("hayes_street_shops", "Hayes Street Boutiques", PlaceType.SHOPPING, 37.7755, -122.4245, 4.5f, true, 0, 0),
            // Mission District
            AppPlace("valencia_street", "Valencia Street Shops", PlaceType.SHOPPING, 37.7600, -122.4216, 4.4f, true, 0, 0),
            AppPlace("mission_street_shops", "Mission Street Shopping", PlaceType.SHOPPING, 37.7520, -122.4180, 4.2f, true, 0, 0),
            // Castro
            AppPlace("castro_street", "Castro Street Shopping", PlaceType.SHOPPING, 37.7615, -122.4350, 4.4f, true, 0, 0),
            AppPlace("market_castro", "Market & Castro Shops", PlaceType.SHOPPING, 37.7620, -122.4348, 4.3f, true, 0, 0),
            // Haight-Ashbury
            AppPlace("haight_street", "Haight Street Shopping", PlaceType.SHOPPING, 37.7700, -122.4485, 4.5f, true, 0, 0),
            AppPlace("upper_haight", "Upper Haight Boutiques", PlaceType.SHOPPING, 37.7710, -122.4460, 4.4f, true, 0, 0),
            // Fillmore
            AppPlace("fillmore_street", "Fillmore Street Shopping", PlaceType.SHOPPING, 37.7865, -122.4331, 4.4f, true, 0, 0),
            AppPlace("pacific_heights_shops", "Pacific Heights Shops", PlaceType.SHOPPING, 37.7930, -122.4310, 4.3f, true, 0, 0),
            // Chestnut Street (Marina)
            AppPlace("chestnut_street", "Chestnut Street Shopping", PlaceType.SHOPPING, 37.8020, -122.4340, 4.4f, true, 0, 0),
            AppPlace("marina_district_shops", "Marina District Boutiques", PlaceType.SHOPPING, 37.8010, -122.4365, 4.3f, true, 0, 0),
            // Union Street (Cow Hollow)
            AppPlace("union_street", "Union Street Shopping", PlaceType.SHOPPING, 37.7980, -122.4295, 4.4f, true, 0, 0),
            // Polk Street
            AppPlace("polk_street", "Polk Street Shopping", PlaceType.SHOPPING, 37.7960, -122.4200, 4.3f, true, 0, 0),
            // Clement Street (Inner Richmond)
            AppPlace("clement_street", "Clement Street Shopping", PlaceType.SHOPPING, 37.7825, -122.4620, 4.4f, true, 0, 0),
            AppPlace("new_chinatown", "New Chinatown Clement", PlaceType.SHOPPING, 37.7815, -122.4630, 4.3f, true, 0, 0),
            // Japantown
            AppPlace("japan_center", "Japan Center", PlaceType.SHOPPING, 37.7850, -122.4305, 4.4f, true, 0, 0),
            AppPlace("japantown_shops", "Japantown Shopping", PlaceType.SHOPPING, 37.7853, -122.4307, 4.3f, true, 0, 0),
            // Chinatown
            AppPlace("chinatown_shops", "Chinatown Shopping District", PlaceType.SHOPPING, 37.7955, -122.4066, 4.3f, true, 0, 0),
            AppPlace("grant_avenue", "Grant Avenue Chinatown", PlaceType.SHOPPING, 37.7945, -122.4072, 4.2f, true, 0, 0),
            // Noe Valley
            AppPlace("24th_street_noe", "24th Street Noe Valley", PlaceType.SHOPPING, 37.7510, -122.4320, 4.5f, true, 0, 0)
        )
    }
}
