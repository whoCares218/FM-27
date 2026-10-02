package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.League

/**
 * The starting club database. Every club is fictional and original: no real club
 * names, badges or trademarks are used, which keeps the game legally safe while
 * still feeling like a believable European football pyramid.
 *
 * Reputation (1..100) drives generated squad quality, board expectations,
 * prize money and AI transfer behaviour.
 */
object ClubDatabase {

    private data class Seed(
        val name: String,
        val short: String,
        val rep: Int,
        val stadium: String,
        val capacity: Int,
        val primary: Int,
        val secondary: Int,
        val target: Int
    )

    private val premierLeague = listOf(
        Seed("Northgate United", "NGU", 92, "Northgate Park", 61_000, 0xB3121B, 0xFFFFFF, 4),
        Seed("Riverton FC", "RIV", 90, "Riverton Arena", 54_000, 0x1B3FA0, 0xF2C200, 4),
        Seed("Kingsbridge City", "KBC", 89, "Kingsbridge Stadium", 53_000, 0x6CBBF5, 0x0B1220, 3),
        Seed("Ashcroft Rovers", "ASH", 86, "The Ashcroft", 41_000, 0xC81E28, 0x0B1220, 6),
        Seed("Thornbury Town", "THB", 84, "Thornbury Ground", 38_000, 0x14213D, 0xE8B33C, 8),
        Seed("Westerly Wanderers", "WES", 82, "Westerly Park", 33_000, 0x0E7C4A, 0xFFFFFF, 10),
        Seed("Harborough FC", "HAR", 80, "Harborough Bowl", 30_500, 0xF2A81D, 0x14213D, 12),
        Seed("Brackenfield", "BRK", 78, "Bracken Field", 28_000, 0x7A1FA2, 0xFFFFFF, 13),
        Seed("Sablewood United", "SAB", 76, "Sablewood Park", 26_500, 0x0B1220, 0x1FA55C, 15),
        Seed("Cranmere Athletic", "CRA", 74, "Cranmere Road", 24_000, 0x2C6FB5, 0xFFFFFF, 16),
        Seed("Dunmoor Rangers", "DUN", 72, "Dunmoor Stadium", 22_000, 0xD9531E, 0x0B1220, 17),
        Seed("Elmsworth FC", "ELM", 70, "Elmsworth Park", 20_500, 0x1FA55C, 0xFFFFFF, 18),
        Seed("Fairhaven Town", "FAI", 68, "Fairhaven Ground", 19_000, 0x8C1F3A, 0xF2C200, 19),
        Seed("Glenmoor City", "GLE", 66, "Glenmoor Arena", 18_000, 0x2E3C5C, 0xFFFFFF, 20),
        Seed("Hollybrook United", "HOL", 64, "Hollybrook Park", 17_000, 0xF2C200, 0x14213D, 20),
        Seed("Iversley Town", "IVE", 62, "Iversley Stadium", 16_000, 0x1B6E9E, 0xFFFFFF, 20)
    )

    private val championship = listOf(
        Seed("Marchwood FC", "MAR", 60, "Marchwood Park", 30_000, 0x1FA55C, 0x0B1220, 1),
        Seed("Netherby United", "NET", 58, "Netherby Arena", 27_000, 0xB3121B, 0xFFFFFF, 2),
        Seed("Oakvale Rovers", "OAK", 57, "Oakvale Ground", 25_000, 0x14213D, 0xF2C200, 3),
        Seed("Pendlebury City", "PEN", 55, "Pendlebury Stadium", 23_000, 0x0E7C4A, 0xFFFFFF, 4),
        Seed("Quarryfield Town", "QUA", 53, "Quarry Field", 21_000, 0xF2A81D, 0x14213D, 6),
        Seed("Rushmere FC", "RUS", 52, "Rushmere Park", 20_000, 0x7A1FA2, 0xFFFFFF, 7),
        Seed("Stonegate Athletic", "STO", 50, "Stonegate Road", 18_500, 0x2C6FB5, 0xFFFFFF, 8),
        Seed("Tarnbrook United", "TAR", 48, "Tarnbrook Bowl", 17_000, 0xD9531E, 0x0B1220, 10),
        Seed("Upperton Wanderers", "UPP", 46, "Upperton Park", 15_500, 0x0B1220, 0x1FA55C, 12),
        Seed("Valewood FC", "VAL", 45, "Vale Wood", 14_500, 0x8C1F3A, 0xF2C200, 13),
        Seed("Westbourne City", "WBC", 44, "Westbourne Arena", 14_000, 0x2E3C5C, 0xFFFFFF, 14),
        Seed("Yarnfield Town", "YAR", 42, "Yarnfield Ground", 13_000, 0x1B6E9E, 0xFFFFFF, 16),
        Seed("Aldergrove FC", "ALD", 40, "Aldergrove Park", 12_500, 0x1FA55C, 0xFFFFFF, 17),
        Seed("Birchwood Rovers", "BIR", 39, "Birchwood Road", 12_000, 0xB3121B, 0xFFFFFF, 18),
        Seed("Coldharbour United", "COL", 38, "Coldharbour Field", 11_500, 0xF2C200, 0x14213D, 19),
        Seed("Denholm Athletic", "DEN", 36, "Denholm Park", 11_000, 0x14213D, 0xFFFFFF, 20)
    )

    private val laLiga = listOf(
        Seed("Real Vallecas", "RVA", 91, "Estadio Vallecas", 58_000, 0xFFFFFF, 0x1B3FA0, 4),
        Seed("Atletico Sierra", "ASI", 89, "Estadio Sierra", 52_000, 0xB3121B, 0xFFFFFF, 3),
        Seed("CD Marbella Azul", "MAZ", 87, "Estadio Azul", 49_000, 0x1B3FA0, 0x8C1F3A, 4),
        Seed("Sevilla Norte", "SEN", 84, "Estadio Norte", 42_000, 0xFFFFFF, 0xB3121B, 6),
        Seed("Valencia Costera", "VCO", 82, "Estadio Costera", 38_000, 0xF2C200, 0x14213D, 8),
        Seed("Real Cantabria", "RCA", 79, "Estadio Cantabria", 32_000, 0x1FA55C, 0xFFFFFF, 10),
        Seed("Bilbao Costa", "BCO", 77, "Estadio Costa", 30_000, 0xB3121B, 0xFFFFFF, 11),
        Seed("Girona Sur", "GSU", 74, "Estadio Sur", 26_000, 0x8C1F3A, 0xF2C200, 13),
        Seed("Zaragoza Rojo", "ZRO", 72, "Estadio Rojo", 24_000, 0xC81E28, 0xFFFFFF, 15),
        Seed("Las Palmas Verde", "LPV", 70, "Estadio Verde", 22_000, 0xF2C200, 0x14213D, 16),
        Seed("Vigo Atlantico", "VAT", 68, "Estadio Atlantico", 20_500, 0x1B6E9E, 0xFFFFFF, 17),
        Seed("Murcia Celeste", "MCE", 66, "Estadio Celeste", 19_000, 0x6CBBF5, 0x0B1220, 18),
        Seed("Alaves Blanco", "ALB", 64, "Estadio Blanco", 18_000, 0xFFFFFF, 0x14213D, 19),
        Seed("Cadiz Amarillo", "CAM", 62, "Estadio Amarillo", 17_000, 0xF2C200, 0x0B1220, 20),
        Seed("Elche Franjiverde", "ELF", 60, "Estadio Franjiverde", 16_000, 0x0E7C4A, 0xFFFFFF, 20),
        Seed("Oviedo Azul", "OVA", 58, "Estadio Azul Norte", 15_000, 0x2C6FB5, 0xFFFFFF, 20)
    )

    private val serieA = listOf(
        Seed("Inter Marittima", "IMA", 90, "Stadio Marittima", 60_000, 0x1B3FA0, 0x0B1220, 4),
        Seed("Milanese Nord", "MNO", 89, "Stadio Nord", 55_000, 0xC81E28, 0x0B1220, 4),
        Seed("Juventina Reale", "JRE", 88, "Stadio Reale", 52_000, 0x0B1220, 0xFFFFFF, 4),
        Seed("Roma Flaminia", "RFL", 85, "Stadio Flaminia", 46_000, 0x8C1F3A, 0xF2C200, 6),
        Seed("Napoli Vesuvio", "NVE", 84, "Stadio Vesuvio", 44_000, 0x1B6E9E, 0xFFFFFF, 6),
        Seed("Fiorentina Arno", "FAR", 80, "Stadio Arno", 36_000, 0x7A1FA2, 0xFFFFFF, 9),
        Seed("Atalanta Orobica", "AOR", 78, "Stadio Orobica", 30_000, 0x1B3FA0, 0x0B1220, 10),
        Seed("Lazio Tiberina", "LTI", 77, "Stadio Tiberina", 32_000, 0x6CBBF5, 0xFFFFFF, 10),
        Seed("Torino Mole", "TMO", 74, "Stadio Mole", 27_000, 0x8C1F3A, 0xFFFFFF, 13),
        Seed("Bologna Rossoblu", "BRO", 72, "Stadio Rossoblu", 25_000, 0xC81E28, 0x1B3FA0, 14),
        Seed("Genoa Lanterna", "GLA", 70, "Stadio Lanterna", 23_000, 0x8C1F3A, 0x1B3FA0, 16),
        Seed("Udinese Friuli", "UFR", 68, "Stadio Friuli", 21_000, 0x0B1220, 0xFFFFFF, 17),
        Seed("Cagliari Sardo", "CSA", 66, "Stadio Sardo", 19_500, 0x8C1F3A, 0x1B3FA0, 18),
        Seed("Verona Scaligera", "VSC", 64, "Stadio Scaligera", 18_000, 0xF2C200, 0x1B3FA0, 19),
        Seed("Empoli Arno Sud", "EAS", 62, "Stadio Arno Sud", 17_000, 0x6CBBF5, 0xFFFFFF, 20),
        Seed("Lecce Salento", "LSA", 60, "Stadio Salento", 16_000, 0xF2C200, 0xC81E28, 20)
    )

    private val bundesliga = listOf(
        Seed("Bavaria Munchen", "BMU", 93, "Arena Munchen", 75_000, 0xC81E28, 0xFFFFFF, 1),
        Seed("Dortmund Westfalen", "DWE", 88, "Westfalen Arena", 66_000, 0xF2C200, 0x0B1220, 3),
        Seed("Leipzig Sachsen", "LSA", 85, "Sachsen Arena", 47_000, 0xFFFFFF, 0xC81E28, 4),
        Seed("Leverkusen Rhein", "LRH", 84, "Rhein Arena", 44_000, 0xC81E28, 0x0B1220, 4),
        Seed("Frankfurt Adler", "FAD", 80, "Adler Arena", 42_000, 0xC81E28, 0x0B1220, 6),
        Seed("Stuttgart Neckar", "SNE", 78, "Neckar Arena", 40_000, 0xFFFFFF, 0xC81E28, 8),
        Seed("Freiburg Schwarzwald", "FSW", 74, "Schwarzwald Arena", 34_000, 0xC81E28, 0x0B1220, 10),
        Seed("Wolfsburg Auto", "WAU", 73, "Auto Arena", 30_000, 0x1FA55C, 0xFFFFFF, 11),
        Seed("Mainz Rheinland", "MRL", 71, "Rheinland Arena", 28_000, 0xC81E28, 0xFFFFFF, 13),
        Seed("Augsburg Schwaben", "ASC", 69, "Schwaben Arena", 26_000, 0x1B3FA0, 0xFFFFFF, 15),
        Seed("Bremen Weser", "BWE", 70, "Weser Arena", 27_000, 0x0E7C4A, 0xFFFFFF, 14),
        Seed("Hoffenheim Kraichgau", "HKR", 68, "Kraichgau Arena", 25_000, 0x1B3FA0, 0xFFFFFF, 16),
        Seed("Union Berliner", "UBE", 67, "Berliner Arena", 24_000, 0xC81E28, 0xFFFFFF, 17),
        Seed("Bochum Ruhr", "BRU", 64, "Ruhr Arena", 22_000, 0x1B3FA0, 0xFFFFFF, 18),
        Seed("Heidenheim Ostalb", "HOA", 62, "Ostalb Arena", 20_000, 0x1B3FA0, 0xC81E28, 20),
        Seed("Koln Dom", "KDO", 66, "Dom Arena", 23_000, 0xC81E28, 0xFFFFFF, 18)
    )

    private val ligue1 = listOf(
        Seed("Paris Royale", "PRO", 92, "Stade Royale", 62_000, 0x14213D, 0xC81E28, 1),
        Seed("Marseille Phocea", "MPH", 86, "Stade Phocea", 48_000, 0x6CBBF5, 0xFFFFFF, 4),
        Seed("Lyonnaise Rhone", "LRH", 84, "Stade Rhone", 45_000, 0x1B3FA0, 0xC81E28, 4),
        Seed("Monaco Azur", "MAZ", 83, "Stade Azur", 40_000, 0xC81E28, 0xFFFFFF, 4),
        Seed("Lille Nord", "LNO", 80, "Stade Nord", 38_000, 0xC81E28, 0x1B3FA0, 6),
        Seed("Rennes Bretagne", "RBR", 78, "Stade Bretagne", 34_000, 0xC81E28, 0x0B1220, 8),
        Seed("Nice Cote", "NCO", 76, "Stade Cote", 32_000, 0x0B1220, 0xC81E28, 10),
        Seed("Lens Artois", "LAR", 74, "Stade Artois", 30_000, 0xF2C200, 0xC81E28, 11),
        Seed("Strasbourg Alsace", "SAL", 72, "Stade Alsace", 28_000, 0x1B3FA0, 0xFFFFFF, 13),
        Seed("Toulouse Garonne", "TGA", 70, "Stade Garonne", 26_000, 0x7A1FA2, 0xFFFFFF, 14),
        Seed("Montpellier Herault", "MHE", 68, "Stade Herault", 24_000, 0xF2C200, 0x1B3FA0, 16),
        Seed("Nantes Atlantique", "NAT", 67, "Stade Atlantique", 23_000, 0xF2C200, 0x0E7C4A, 17),
        Seed("Brest Finistere", "BFI", 65, "Stade Finistere", 21_000, 0xC81E28, 0xFFFFFF, 18),
        Seed("Reims Champagne", "RCH", 64, "Stade Champagne", 20_000, 0xC81E28, 0xFFFFFF, 18),
        Seed("Auxerre Bourgogne", "ABO", 62, "Stade Bourgogne", 18_000, 0x1B3FA0, 0xFFFFFF, 20),
        Seed("Le Havre Normandie", "LHN", 60, "Stade Normandie", 17_000, 0x1B3FA0, 0x6CBBF5, 20)
    )

    private val byLeague: Map<String, List<Seed>> = mapOf(
        League.PREMIER_LEAGUE.id to premierLeague,
        League.CHAMPIONSHIP.id to championship,
        League.LA_LIGA.id to laLiga,
        League.SERIE_A.id to serieA,
        League.BUNDESLIGA.id to bundesliga,
        League.LIGUE_1.id to ligue1
    )

    /** Total clubs in the starting database. */
    val clubCount: Int = byLeague.values.sumOf { it.size }

    /**
     * Builds every club. Budgets are derived from reputation so a top club can
     * spend like a top club while a promoted side must be frugal.
     */
    fun buildAll(): List<Club> {
        val clubs = mutableListOf<Club>()
        var id = 1L
        for (league in League.all) {
            val seeds = byLeague[league.id] ?: continue
            for (seed in seeds) {
                val reputationFactor = seed.rep / 100.0
                val transferBudget = when {
                    league.tier == 2 -> (seed.rep * seed.rep * 2_400L).coerceAtLeast(500_000L)
                    seed.rep >= 88 -> (seed.rep * seed.rep * 260_000L)
                    seed.rep >= 78 -> (seed.rep * seed.rep * 150_000L)
                    seed.rep >= 70 -> (seed.rep * seed.rep * 90_000L)
                    else -> (seed.rep * seed.rep * 45_000L)
                }
                val wageBudget = (transferBudget * 11L / 100L).coerceAtLeast(120_000L)
                val balance = (transferBudget * 45L / 100L).coerceAtLeast(1_000_000L)

                clubs += Club(
                    id = id++,
                    name = seed.name,
                    shortName = seed.short,
                    country = league.country,
                    leagueId = league.id,
                    reputation = seed.rep,
                    stadiumName = seed.stadium,
                    stadiumCapacity = seed.capacity,
                    balance = balance,
                    transferBudget = transferBudget,
                    wageBudget = wageBudget,
                    boardExpectation = expectationText(seed.target),
                    targetLeaguePosition = seed.target,
                    primaryColor = seed.primary,
                    secondaryColor = seed.secondary,
                    formationId = defaultFormationFor(seed.rep, reputationFactor)
                )
            }
        }
        return clubs
    }

    private fun expectationText(target: Int): String = when {
        target <= 2 -> "Win the league title"
        target <= 4 -> "Qualify for the Champions League (top 4)"
        target <= 6 -> "Qualify for European competition (top 6)"
        target <= 10 -> "Finish in the top half"
        target <= 15 -> "Secure a comfortable mid-table finish"
        target <= 18 -> "Avoid a relegation battle"
        else -> "Avoid relegation"
    }

    /** Stronger sides start with more expansive shapes; weaker sides are more conservative. */
    private fun defaultFormationFor(reputation: Int, factor: Double): String = when {
        reputation >= 86 -> Formation.F433.id
        reputation >= 78 -> Formation.F4231.id
        reputation >= 70 -> Formation.F4231.id
        reputation >= 62 -> Formation.F442.id
        else -> Formation.F532.id
    }
}
