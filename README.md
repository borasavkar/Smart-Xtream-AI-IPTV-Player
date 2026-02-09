📺 SmartXtream - Next Gen AI-Powered IPTV Player

SmartXtream is not just another IPTV player. It is an intelligent, high-performance media streaming application designed for both Android Mobile and Android TV.

It features a "Liquid Glass" UI with neon-focus states for TV remotes, a Hybrid Buffer System that adapts to device RAM (solving freezing issues on low-end TVs), and an On-Device AI Recommendation Engine that learns from your viewing habits.
✨ Key Features
🧠 1. AI Recommendation Engine (On-Device)

Unlike standard players, SmartXtream analyzes your taste locally without sending data to external servers.

    Behavioral Analysis: Tracks what you watch and for how long.

    Scoring System:

        ❤️ Favorites: +50 Points (High Signal)

        ⏱️ Watch Time: +0.5 Points per minute (Medium Signal)

    Metadata Parsing: Analyzes Genres, Cast, and Directors to suggest content based on year (Newest First) and Relevance Score.

⚡ 2. Hybrid Buffer Architecture (Smart RAM Management)

Solves the fragmentation problem in the Android ecosystem. The player automatically detects available RAM and assigns a buffer profile:

    Economic Mode (1.5GB RAM TVs): Locks buffer to 30MB to prevent system crashes/freezes.

    Ultra Mode (High-End Devices): Expands buffer to 350MB for instant seeking and 4K playback.

🎨 3. Liquid Glass UI & TV Optimization

    Aesthetic: Modern, semi-transparent glass morphism design.

    TV Experience: Custom StateListDrawables provide a Neon Green Focus effect when navigating with a TV remote, ensuring 100% accessibility.

🛡️ 4. Smart Error Recovery & Maintenance

    Auto-Fallback: If a stream (.ts) fails due to codec or network errors, the player automatically cleans the cache and retries with an alternative format (.m3u8).

    Self-Cleaning: An automated background process monitors cache size and cleans corrupt video segments to keep the app running smoothly.

📸 Screenshots
Android TV (Home)	Mobile (Player)
	
Neon Focus Navigation	Advanced Player Controls
🛠️ Tech Stack

    Language: Kotlin

    Architecture: MVVM (Model-View-ViewModel)

    Video Player: AndroidX Media3 (ExoPlayer) with HLS/TS support.

    Database: Room Database (SQLite) with custom DAOs.

    Networking: Retrofit2 & OkHttp3.

    Image Loading: Glide (Optimized for caching).

    Concurrency: Kotlin Coroutines & Flow.

    Billing: Google Play Billing Library (Subscription management).

🧩 Code Highlights
The AI Logic (PreferenceManager.kt)

How we calculate what the user loves:
Kotlin

suspend fun analyzeAndStore(context: Context, profileId: Int, meta: MetaData, points: Double) {
    val db = AppDatabase.getInstance(context).userPreferenceDao()
    
    // Split and score metadata
    val genres = splitAndClean(meta.genre)
    val cast = splitAndClean(meta.cast)
    
    // Store in Room DB with timestamp
    saveTags(db, profileId, "GENRE", genres, points)
    saveTags(db, profileId, "CAST", cast, points)
}

The Hybrid Buffer (PlayerActivity.kt)

Dynamic load control based on device capability:
Kotlin

val profile = when {
    isLowMemory -> BufferProfile(30, "Economic Mode") // For Beko/Vestel TVs
    totalRamMB < 4096 -> BufferProfile(120, "Standard Mode") // For Mid-range Phones
    else -> BufferProfile(350, "Ultra Performance") // For Shield TV / Flagships
}

📥 Installation

    Clone the repository:
    Bash

    git clone https://github.com/yourusername/SmartXtream.git

    Open in Android Studio.

    Sync Gradle (Min SDK: 24, Target SDK: 34+).

    Build the APK or App Bundle.

Note: You need to configure your own google-services.json for Firebase integration.
🤝 Contributing

Contributions are welcome! Please follow these steps:

    Fork the project.

    Create your feature branch (git checkout -b feature/AmazingFeature).

    Commit your changes (git commit -m 'Add some AmazingFeature').

    Push to the branch (git push origin feature/AmazingFeature).

    Open a Pull Request.

📜 License

Distributed under the MIT License. See LICENSE for more information.

<p align="center"> Developed with ❤️ by <b>Bora Şavkar</b> </p>
