package com.footymanager.simulator.domain.data

/**
 * Fictional name pools used to build a legally safe player database.
 * Names are common given/family names rather than any real player, and are
 * combined pseudo-randomly so no generated player maps to a real person.
 */
object NameData {

    private val englishFirst = listOf(
        "James", "Oliver", "Harry", "Jack", "Charlie", "George", "Alfie", "Leo", "Freddie", "Archie",
        "Callum", "Ethan", "Liam", "Mason", "Tyler", "Kieran", "Declan", "Reece", "Aaron", "Josh",
        "Nathan", "Connor", "Bailey", "Harvey", "Elliot", "Toby", "Riley", "Finley", "Louie", "Max",
        "Dylan", "Owen", "Rhys", "Lewis", "Morgan", "Blake", "Corey", "Ashton", "Kai", "Jude"
    )
    private val englishLast = listOf(
        "Walker", "Harrison", "Bennett", "Foster", "Hayes", "Dawson", "Palmer", "Reed", "Grant", "Whitaker",
        "Barlow", "Mercer", "Sinclair", "Ashworth", "Hollis", "Rowley", "Kendrick", "Bramley", "Corbett", "Naylor",
        "Ainsley", "Thornton", "Redfern", "Ellison", "Pemberton", "Marlowe", "Sterling", "Ashby", "Cornish", "Delaney",
        "Fairbrother", "Grimshaw", "Harlow", "Ingham", "Jarvis", "Kingsley", "Lockwood", "Marsden", "Norwood", "Oakley"
    )

    private val spanishFirst = listOf(
        "Alejandro", "Mateo", "Hugo", "Pablo", "Daniel", "Adrian", "Alvaro", "Iker", "Nicolas", "Sergio",
        "Marco", "Javier", "Diego", "Ruben", "Bruno", "Gonzalo", "Aitor", "Unai", "Joaquin", "Rodrigo",
        "Ander", "Ivan", "Mario", "Bryan", "Lucas", "Enzo", "Cesar", "Hector", "Raul", "Isco"
    )
    private val spanishLast = listOf(
        "Moreno", "Castillo", "Navarro", "Iglesias", "Fuentes", "Vidal", "Serrano", "Cabrera", "Peralta", "Bermejo",
        "Cordero", "Quintana", "Salazar", "Montoya", "Alcaraz", "Perales", "Guzman", "Rivas", "Escobar", "Trujillo",
        "Zamora", "Delgado", "Ocampo", "Barrios", "Cuesta", "Herrera", "Lozano", "Mendez", "Pineda", "Robles"
    )

    private val italianFirst = listOf(
        "Lorenzo", "Francesco", "Alessandro", "Matteo", "Leonardo", "Riccardo", "Gabriele", "Tommaso", "Andrea", "Davide",
        "Federico", "Nicolo", "Simone", "Giuseppe", "Marco", "Antonio", "Stefano", "Emanuele", "Cristian", "Manuel",
        "Samuele", "Filippo", "Michele", "Pietro", "Vincenzo", "Salvatore", "Daniele", "Alberto", "Giacomo", "Edoardo"
    )
    private val italianLast = listOf(
        "Bianchi", "Ferrari", "Romano", "Esposito", "Ricci", "Marino", "Greco", "Bruno", "Gallo", "Conti",
        "De Luca", "Costa", "Giordano", "Mancini", "Rizzo", "Lombardi", "Moretti", "Barbieri", "Fontana", "Santoro",
        "Mariani", "Rinaldi", "Caruso", "Ferrara", "Galli", "Martini", "Leone", "Longo", "Gentile", "Martinelli"
    )

    private val germanFirst = listOf(
        "Lukas", "Finn", "Jonas", "Leon", "Elias", "Noah", "Ben", "Paul", "Felix", "Maximilian",
        "Julian", "Moritz", "Niklas", "Tim", "Jan", "Fabian", "Simon", "David", "Philipp", "Marvin",
        "Tobias", "Dominik", "Sebastian", "Florian", "Kevin", "Marco", "Marcel", "Pascal", "Robin", "Sven"
    )
    private val germanLast = listOf(
        "Muller", "Schmidt", "Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker", "Hoffmann", "Schulz",
        "Koch", "Bauer", "Richter", "Klein", "Wolf", "Neumann", "Schwarz", "Zimmermann", "Braun", "Kruger",
        "Hartmann", "Lange", "Werner", "Krause", "Meier", "Lehmann", "Kohler", "Herrmann", "Walter", "Konig"
    )

    private val frenchFirst = listOf(
        "Lucas", "Enzo", "Hugo", "Louis", "Gabriel", "Raphael", "Arthur", "Jules", "Adam", "Nathan",
        "Theo", "Noah", "Ethan", "Mathis", "Antoine", "Clement", "Baptiste", "Maxime", "Quentin", "Romain",
        "Yanis", "Ilyes", "Bilal", "Amine", "Karim", "Sofiane", "Mehdi", "Rayane", "Alexis", "Valentin"
    )
    private val frenchLast = listOf(
        "Dubois", "Lefevre", "Moreau", "Laurent", "Simon", "Michel", "Leroy", "Roux", "David", "Bertrand",
        "Morel", "Fournier", "Girard", "Bonnet", "Dupont", "Lambert", "Fontaine", "Rousseau", "Vincent", "Muller",
        "Lefebvre", "Faure", "Andre", "Mercier", "Blanc", "Guerin", "Boyer", "Garnier", "Chevalier", "Francois"
    )

    /** African / South American names add squad diversity to European leagues. */
    private val internationalFirst = listOf(
        "Kofi", "Kwame", "Chidi", "Emeka", "Tunde", "Sekou", "Amadou", "Ibrahim", "Youssef", "Anas",
        "Victor", "Samuel", "Emmanuel", "Blessing", "Joel", "Rafael", "Thiago", "Matheus", "Gabriel", "Joao",
        "Lautaro", "Facundo", "Santiago", "Nahuel", "Julian", "Bruno", "Alexis", "Enzo", "Diego", "Renan"
    )
    private val internationalLast = listOf(
        "Mensah", "Owusu", "Boateng", "Okafor", "Adeyemi", "Diallo", "Toure", "Traore", "Keita", "Camara",
        "Silva", "Santos", "Oliveira", "Pereira", "Costa", "Almeida", "Rodrigues", "Fernandes", "Gomes", "Martins",
        "Gonzalez", "Rodriguez", "Martinez", "Lopez", "Fernandez", "Romero", "Alvarez", "Sosa", "Cabral", "Nascimento"
    )

    private val nationalities = listOf(
        "England", "Spain", "Italy", "Germany", "France", "Portugal", "Netherlands", "Belgium",
        "Brazil", "Argentina", "Ghana", "Nigeria", "Senegal", "Morocco", "Croatia", "Denmark",
        "Sweden", "Norway", "Ireland", "Scotland", "Wales", "Uruguay", "Colombia", "Japan", "South Korea"
    )

    /** Names for AI managers, used in news items. */
    private val managerFirst = englishFirst + spanishFirst + italianFirst + germanFirst
    private val managerLast = englishLast + spanishLast + italianLast + germanLast

    data class NamePool(val first: List<String>, val last: List<String>)

    private fun poolFor(country: String): NamePool = when (country) {
        "England" -> NamePool(englishFirst, englishLast)
        "Spain" -> NamePool(spanishFirst, spanishLast)
        "Italy" -> NamePool(italianFirst, italianLast)
        "Germany" -> NamePool(germanFirst, germanLast)
        "France" -> NamePool(frenchFirst, frenchLast)
        else -> NamePool(englishFirst, englishLast)
    }

    fun firstName(country: String, random: kotlin.random.Random): String =
        poolFor(country).first[random.nextInt(poolFor(country).first.size)]

    fun lastName(country: String, random: kotlin.random.Random): String =
        poolFor(country).last[random.nextInt(poolFor(country).last.size)]

    fun internationalName(random: kotlin.random.Random): String =
        "${internationalFirst[random.nextInt(internationalFirst.size)]} " +
            internationalLast[random.nextInt(internationalLast.size)]

    fun managerName(random: kotlin.random.Random): String =
        "${managerFirst[random.nextInt(managerFirst.size)]} ${managerLast[random.nextInt(managerLast.size)]}"

    fun nationality(random: kotlin.random.Random): String =
        nationalities[random.nextInt(nationalities.size)]

    /** A nationality typical for the given league's country, with imported players mixed in. */
    fun nationalityForLeague(country: String, random: kotlin.random.Random): String =
        if (random.nextDouble() < 0.58) country else nationalities[random.nextInt(nationalities.size)]
}
