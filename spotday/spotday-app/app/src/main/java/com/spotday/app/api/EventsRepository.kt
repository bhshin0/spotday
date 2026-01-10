package com.spotday.app.api

import android.util.Log
import com.spotday.app.model.Event
import com.spotday.app.model.EventType

/**
 * Repository for events in San Francisco.
 * Currently uses hardcoded mock data for testing.
 * Will be replaced with real API integration (Ticketmaster, Eventbrite, etc.) later.
 */
class EventsRepository {

    /**
     * Get all available events for the selected time window.
     * @param startHour The earliest hour the user wants to start (0-23)
     * @param endHour The latest hour the user wants to end (0-23)
     * @return List of events that start within the time window
     */
    suspend fun getEventsForTimeWindow(startHour: Int, endHour: Int): List<Event> {
        Log.d("EventsRepository", "Getting events for time window $startHour - $endHour")
        return getAllMockEvents().filter { event ->
            event.startHour >= startHour && event.startHour < endHour
        }
    }

    /**
     * Get events by their IDs.
     */
    suspend fun getEventsByIds(eventIds: List<String>): List<Event> {
        return getAllMockEvents().filter { it.id in eventIds }
    }

    /**
     * Get all mock events - comprehensive SF event data for testing.
     */
    private fun getAllMockEvents(): List<Event> {
        return listOf(
            // ==================== CONCERTS ====================
            // Chase Center - Major arena shows
            Event(
                id = "concert_chase_1",
                name = "Taylor Swift - Eras Tour",
                description = "The iconic Eras Tour comes to San Francisco",
                eventType = EventType.CONCERT,
                venueName = "Chase Center",
                venueLatitude = 37.7679,
                venueLongitude = -122.3873,
                startHour = 19,
                startMinute = 30,
                durationMinutes = 180,
                priceMin = 150.0,
                priceMax = 850.0,
                isSoldOut = true,
                ticketUrl = "https://ticketmaster.com/taylor-swift"
            ),
            Event(
                id = "concert_chase_2",
                name = "Kendrick Lamar",
                description = "The Big Steppers Tour",
                eventType = EventType.CONCERT,
                venueName = "Chase Center",
                venueLatitude = 37.7679,
                venueLongitude = -122.3873,
                startHour = 20,
                startMinute = 0,
                durationMinutes = 150,
                priceMin = 85.0,
                priceMax = 350.0,
                isSoldOut = false,
                ticketUrl = "https://ticketmaster.com/kendrick"
            ),
            // The Fillmore - Historic venue
            Event(
                id = "concert_fillmore_1",
                name = "Khruangbin",
                description = "Texas funk trio live at The Fillmore",
                eventType = EventType.CONCERT,
                venueName = "The Fillmore",
                venueLatitude = 37.7832,
                venueLongitude = -122.4333,
                startHour = 20,
                startMinute = 0,
                durationMinutes = 120,
                priceMin = 45.0,
                priceMax = 75.0,
                isSoldOut = false,
                ticketUrl = "https://livenation.com/fillmore"
            ),
            Event(
                id = "concert_fillmore_2",
                name = "Japanese Breakfast",
                description = "Indie pop sensation Michelle Zauner",
                eventType = EventType.CONCERT,
                venueName = "The Fillmore",
                venueLatitude = 37.7832,
                venueLongitude = -122.4333,
                startHour = 21,
                startMinute = 0,
                durationMinutes = 90,
                priceMin = 35.0,
                priceMax = 55.0,
                isSoldOut = false,
                ticketUrl = "https://livenation.com/fillmore"
            ),
            // Great American Music Hall
            Event(
                id = "concert_gamh_1",
                name = "Local Natives",
                description = "Indie rock with soaring harmonies",
                eventType = EventType.CONCERT,
                venueName = "Great American Music Hall",
                venueLatitude = 37.7839,
                venueLongitude = -122.4209,
                startHour = 20,
                startMinute = 30,
                durationMinutes = 120,
                priceMin = 30.0,
                priceMax = 45.0,
                isSoldOut = false,
                ticketUrl = "https://gamh.com"
            ),
            // The Chapel
            Event(
                id = "concert_chapel_1",
                name = "Faye Webster",
                description = "Atlanta singer-songwriter live",
                eventType = EventType.CONCERT,
                venueName = "The Chapel",
                venueLatitude = 37.7535,
                venueLongitude = -122.4220,
                startHour = 21,
                startMinute = 0,
                durationMinutes = 90,
                priceMin = 25.0,
                priceMax = 40.0,
                isSoldOut = false,
                ticketUrl = "https://thechapelsf.com"
            ),
            // Bottom of the Hill
            Event(
                id = "concert_boh_1",
                name = "Local Band Showcase",
                description = "Three up-and-coming SF bands",
                eventType = EventType.CONCERT,
                venueName = "Bottom of the Hill",
                venueLatitude = 37.7512,
                venueLongitude = -122.3983,
                startHour = 20,
                startMinute = 0,
                durationMinutes = 180,
                priceMin = 12.0,
                priceMax = 15.0,
                isSoldOut = false,
                ticketUrl = "https://bottomofthehill.com"
            ),
            
            // ==================== SPORTS ====================
            // Oracle Park - Giants
            Event(
                id = "sports_giants_1",
                name = "Giants vs. Dodgers",
                description = "SF Giants take on rival LA Dodgers",
                eventType = EventType.SPORTS,
                venueName = "Oracle Park",
                venueLatitude = 37.7785,
                venueLongitude = -122.3893,
                startHour = 18,
                startMinute = 45,
                durationMinutes = 180,
                priceMin = 25.0,
                priceMax = 250.0,
                isSoldOut = false,
                ticketUrl = "https://mlb.com/giants"
            ),
            Event(
                id = "sports_giants_2",
                name = "Giants vs. Padres",
                description = "Divisional matchup at Oracle Park",
                eventType = EventType.SPORTS,
                venueName = "Oracle Park",
                venueLatitude = 37.7785,
                venueLongitude = -122.3893,
                startHour = 13,
                startMinute = 5,
                durationMinutes = 180,
                priceMin = 20.0,
                priceMax = 180.0,
                isSoldOut = false,
                ticketUrl = "https://mlb.com/giants"
            ),
            // Chase Center - Warriors
            Event(
                id = "sports_warriors_1",
                name = "Warriors vs. Lakers",
                description = "Golden State hosts the Lakers",
                eventType = EventType.SPORTS,
                venueName = "Chase Center",
                venueLatitude = 37.7679,
                venueLongitude = -122.3873,
                startHour = 19,
                startMinute = 30,
                durationMinutes = 150,
                priceMin = 75.0,
                priceMax = 500.0,
                isSoldOut = false,
                ticketUrl = "https://nba.com/warriors"
            ),
            Event(
                id = "sports_warriors_2",
                name = "Warriors vs. Celtics",
                description = "Finals rematch at Chase Center",
                eventType = EventType.SPORTS,
                venueName = "Chase Center",
                venueLatitude = 37.7679,
                venueLongitude = -122.3873,
                startHour = 17,
                startMinute = 30,
                durationMinutes = 150,
                priceMin = 95.0,
                priceMax = 650.0,
                isSoldOut = false,
                ticketUrl = "https://nba.com/warriors"
            ),
            // Levi's Stadium - 49ers (technically Santa Clara but close enough)
            Event(
                id = "sports_49ers_1",
                name = "49ers vs. Seahawks",
                description = "NFC West rivalry game",
                eventType = EventType.SPORTS,
                venueName = "Levi's Stadium",
                venueLatitude = 37.4033,
                venueLongitude = -121.9695,
                startHour = 13,
                startMinute = 5,
                durationMinutes = 210,
                priceMin = 85.0,
                priceMax = 450.0,
                isSoldOut = false,
                ticketUrl = "https://49ers.com"
            ),

            // ==================== THEATER ====================
            // Orpheum Theatre
            Event(
                id = "theater_orpheum_1",
                name = "Hamilton",
                description = "The revolutionary musical about Alexander Hamilton",
                eventType = EventType.THEATER,
                venueName = "Orpheum Theatre",
                venueLatitude = 37.7795,
                venueLongitude = -122.4137,
                startHour = 19,
                startMinute = 30,
                durationMinutes = 165,
                priceMin = 75.0,
                priceMax = 350.0,
                isSoldOut = false,
                ticketUrl = "https://broadwaysf.com"
            ),
            Event(
                id = "theater_orpheum_2",
                name = "Wicked",
                description = "The untold story of the witches of Oz",
                eventType = EventType.THEATER,
                venueName = "Orpheum Theatre",
                venueLatitude = 37.7795,
                venueLongitude = -122.4137,
                startHour = 14,
                startMinute = 0,
                durationMinutes = 165,
                priceMin = 65.0,
                priceMax = 280.0,
                isSoldOut = false,
                ticketUrl = "https://broadwaysf.com"
            ),
            // SF Playhouse
            Event(
                id = "theater_playhouse_1",
                name = "A Streetcar Named Desire",
                description = "Tennessee Williams classic drama",
                eventType = EventType.THEATER,
                venueName = "SF Playhouse",
                venueLatitude = 37.7875,
                venueLongitude = -122.4073,
                startHour = 19,
                startMinute = 0,
                durationMinutes = 150,
                priceMin = 35.0,
                priceMax = 85.0,
                isSoldOut = false,
                ticketUrl = "https://sfplayhouse.org"
            ),
            // ACT - American Conservatory Theater
            Event(
                id = "theater_act_1",
                name = "Angels in America",
                description = "Tony Kushner's epic drama",
                eventType = EventType.THEATER,
                venueName = "ACT Geary Theater",
                venueLatitude = 37.7876,
                venueLongitude = -122.4108,
                startHour = 19,
                startMinute = 30,
                durationMinutes = 195,
                priceMin = 45.0,
                priceMax = 120.0,
                isSoldOut = false,
                ticketUrl = "https://act-sf.org"
            ),

            // ==================== COMEDY ====================
            // Cobb's Comedy Club
            Event(
                id = "comedy_cobbs_1",
                name = "Hasan Minhaj",
                description = "Stand-up special taping",
                eventType = EventType.COMEDY,
                venueName = "Cobb's Comedy Club",
                venueLatitude = 37.8072,
                venueLongitude = -122.4171,
                startHour = 20,
                startMinute = 0,
                durationMinutes = 90,
                priceMin = 35.0,
                priceMax = 55.0,
                isSoldOut = false,
                ticketUrl = "https://cobbscomedy.com"
            ),
            Event(
                id = "comedy_cobbs_2",
                name = "Ali Wong",
                description = "Netflix special warm-up show",
                eventType = EventType.COMEDY,
                venueName = "Cobb's Comedy Club",
                venueLatitude = 37.8072,
                venueLongitude = -122.4171,
                startHour = 21,
                startMinute = 30,
                durationMinutes = 75,
                priceMin = 45.0,
                priceMax = 75.0,
                isSoldOut = true,
                ticketUrl = "https://cobbscomedy.com"
            ),
            // Punch Line SF
            Event(
                id = "comedy_punch_1",
                name = "Local Comedy Showcase",
                description = "Best of SF comedy scene",
                eventType = EventType.COMEDY,
                venueName = "Punch Line SF",
                venueLatitude = 37.7935,
                venueLongitude = -122.3975,
                startHour = 20,
                startMinute = 0,
                durationMinutes = 90,
                priceMin = 20.0,
                priceMax = 30.0,
                isSoldOut = false,
                ticketUrl = "https://punchlinecomedyclub.com"
            ),

            // ==================== FOOD FESTIVALS ====================
            // Ferry Building
            Event(
                id = "food_ferry_1",
                name = "Ferry Building Farmers Market",
                description = "Weekly outdoor farmers market with local vendors",
                eventType = EventType.FOOD_FESTIVAL,
                venueName = "Ferry Building",
                venueLatitude = 37.7956,
                venueLongitude = -122.3935,
                startHour = 8,
                startMinute = 0,
                durationMinutes = 240,
                priceMin = null,
                priceMax = null,
                isSoldOut = false,
                ticketUrl = null
            ),
            // Off the Grid
            Event(
                id = "food_otg_1",
                name = "Off the Grid: Fort Mason",
                description = "Food truck gathering with live music",
                eventType = EventType.FOOD_FESTIVAL,
                venueName = "Fort Mason Center",
                venueLatitude = 37.8055,
                venueLongitude = -122.4315,
                startHour = 17,
                startMinute = 0,
                durationMinutes = 180,
                priceMin = null,
                priceMax = null,
                isSoldOut = false,
                ticketUrl = "https://offthegrid.com"
            ),
            Event(
                id = "food_otg_2",
                name = "Off the Grid: Presidio Picnic",
                description = "Sunday food truck event at the Presidio",
                eventType = EventType.FOOD_FESTIVAL,
                venueName = "Presidio Main Post",
                venueLatitude = 37.7989,
                venueLongitude = -122.4570,
                startHour = 11,
                startMinute = 0,
                durationMinutes = 240,
                priceMin = null,
                priceMax = null,
                isSoldOut = false,
                ticketUrl = "https://offthegrid.com"
            ),
            // SF Street Food Festival
            Event(
                id = "food_sfsf_1",
                name = "SF Street Food Festival",
                description = "Annual celebration of Bay Area street food",
                eventType = EventType.FOOD_FESTIVAL,
                venueName = "Mission District",
                venueLatitude = 37.7599,
                venueLongitude = -122.4148,
                startHour = 11,
                startMinute = 0,
                durationMinutes = 480,
                priceMin = 10.0,
                priceMax = 15.0,
                isSoldOut = false,
                ticketUrl = "https://lacocina.org"
            ),

            // ==================== STREET FAIRS ====================
            // Castro Street Fair
            Event(
                id = "fair_castro_1",
                name = "Castro Street Fair",
                description = "Annual LGBTQ+ community celebration",
                eventType = EventType.STREET_FAIR,
                venueName = "Castro District",
                venueLatitude = 37.7609,
                venueLongitude = -122.4350,
                startHour = 11,
                startMinute = 0,
                durationMinutes = 480,
                priceMin = null,
                priceMax = null,
                isSoldOut = false,
                ticketUrl = "https://castrostreetfair.org"
            ),
            // Haight Street Fair
            Event(
                id = "fair_haight_1",
                name = "Haight Ashbury Street Fair",
                description = "Summer of Love lives on",
                eventType = EventType.STREET_FAIR,
                venueName = "Haight Street",
                venueLatitude = 37.7700,
                venueLongitude = -122.4485,
                startHour = 10,
                startMinute = 0,
                durationMinutes = 540,
                priceMin = null,
                priceMax = null,
                isSoldOut = false,
                ticketUrl = "https://haightstreetfair.org"
            ),
            // North Beach Festival
            Event(
                id = "fair_nb_1",
                name = "North Beach Festival",
                description = "Italian heritage celebration with food and music",
                eventType = EventType.STREET_FAIR,
                venueName = "North Beach",
                venueLatitude = 37.8001,
                venueLongitude = -122.4102,
                startHour = 10,
                startMinute = 0,
                durationMinutes = 600,
                priceMin = null,
                priceMax = null,
                isSoldOut = false,
                ticketUrl = "https://sfnorthbeach.org"
            ),
            // Dolores Park Event
            Event(
                id = "fair_dolores_1",
                name = "Dolores Park Music Festival",
                description = "Free outdoor concert series",
                eventType = EventType.STREET_FAIR,
                venueName = "Dolores Park",
                venueLatitude = 37.7596,
                venueLongitude = -122.4269,
                startHour = 12,
                startMinute = 0,
                durationMinutes = 360,
                priceMin = null,
                priceMax = null,
                isSoldOut = false,
                ticketUrl = null
            ),

            // ==================== CLASSES & WORKSHOPS ====================
            // Cooking classes
            Event(
                id = "class_cook_1",
                name = "Italian Pasta Making",
                description = "Learn to make fresh pasta from scratch",
                eventType = EventType.CLASS_WORKSHOP,
                venueName = "Sur La Table",
                venueLatitude = 37.7956,
                venueLongitude = -122.3935,
                startHour = 18,
                startMinute = 0,
                durationMinutes = 150,
                priceMin = 85.0,
                priceMax = 95.0,
                isSoldOut = false,
                ticketUrl = "https://surlatable.com"
            ),
            Event(
                id = "class_cook_2",
                name = "Sushi Rolling Workshop",
                description = "Master the art of sushi making",
                eventType = EventType.CLASS_WORKSHOP,
                venueName = "18 Reasons",
                venueLatitude = 37.7616,
                venueLongitude = -122.4190,
                startHour = 14,
                startMinute = 0,
                durationMinutes = 180,
                priceMin = 95.0,
                priceMax = 110.0,
                isSoldOut = false,
                ticketUrl = "https://18reasons.org"
            ),
            // Art workshops
            Event(
                id = "class_art_1",
                name = "Watercolor Painting Basics",
                description = "Intro to watercolor techniques",
                eventType = EventType.CLASS_WORKSHOP,
                venueName = "SF Art Institute",
                venueLatitude = 37.8007,
                venueLongitude = -122.4226,
                startHour = 10,
                startMinute = 0,
                durationMinutes = 180,
                priceMin = 65.0,
                priceMax = 75.0,
                isSoldOut = false,
                ticketUrl = "https://sfai.edu"
            ),
            Event(
                id = "class_art_2",
                name = "Pottery Wheel Workshop",
                description = "Hands-on ceramic pottery class",
                eventType = EventType.CLASS_WORKSHOP,
                venueName = "Clayroom SF",
                venueLatitude = 37.7802,
                venueLongitude = -122.4100,
                startHour = 13,
                startMinute = 0,
                durationMinutes = 150,
                priceMin = 75.0,
                priceMax = 85.0,
                isSoldOut = false,
                ticketUrl = "https://clayroomsf.com"
            ),
            // Wine/Beer tasting
            Event(
                id = "class_wine_1",
                name = "California Wine Tasting",
                description = "Guided tasting of Napa and Sonoma wines",
                eventType = EventType.CLASS_WORKSHOP,
                venueName = "The Barrel Room",
                venueLatitude = 37.7860,
                venueLongitude = -122.4008,
                startHour = 17,
                startMinute = 0,
                durationMinutes = 120,
                priceMin = 55.0,
                priceMax = 65.0,
                isSoldOut = false,
                ticketUrl = "https://thebarrelroomsf.com"
            ),
            Event(
                id = "class_beer_1",
                name = "Craft Beer Brewing 101",
                description = "Learn to brew your own beer",
                eventType = EventType.CLASS_WORKSHOP,
                venueName = "Anchor Brewing",
                venueLatitude = 37.7679,
                venueLongitude = -122.4020,
                startHour = 14,
                startMinute = 0,
                durationMinutes = 180,
                priceMin = 85.0,
                priceMax = 95.0,
                isSoldOut = false,
                ticketUrl = "https://anchorbrewing.com"
            )
        )
    }
}
