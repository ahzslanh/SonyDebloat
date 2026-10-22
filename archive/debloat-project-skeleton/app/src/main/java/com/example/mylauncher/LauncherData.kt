package com.example.mylauncher

/**
 * Hard-coded "Top on Netflix" catalogue.
 *
 * Replace this with a live fetch (Retrofit/OkHttp) once you have an API key;
 * the UI does not care where the list comes from.
 */
object LauncherData {

    data class Poster(
        val id: String,
        val title: String,
        val provider: String,
        val drawableRes: Int
    )

    val items: List<Poster> = listOf(
        Poster("stranger_things", "Stranger Things", "Netflix", R.drawable.stranger_things),
        Poster("the_crown", "The Crown", "Netflix", R.drawable.the_crown),
        Poster("wednesday", "Wednesday", "Netflix", R.drawable.wednesday),
        Poster("squid_game", "Squid Game", "Netflix", R.drawable.squid_game),
        Poster("ozark", "Ozark", "Netflix", R.drawable.ozark),
        Poster("bridgerton", "Bridgerton", "Netflix", R.drawable.bridgerton),
        Poster("black_mirror", "Black Mirror", "Netflix", R.drawable.black_mirror),
        Poster("money_heist", "Money Heist", "Netflix", R.drawable.money_heist),
        Poster("the_witcher", "The Witcher", "Netflix", R.drawable.the_witcher),
        Poster("dark", "Dark", "Netflix", R.drawable.dark)
    )
}
