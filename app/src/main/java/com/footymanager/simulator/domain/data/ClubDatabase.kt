package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.BadgeShape
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.FinanceModel
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.League

/**
 * The starting club database.
 *
 * Every club is an original fictional creation, but the world is deliberately
 * *recognisable*: each major European city has a club whose name, colours, city
 * and nickname evoke the real football landscape without copying any real name,
 * badge, crest or trademark. That gives the player a familiar-feeling universe
 * while staying legally safe.
 *
 * Reputation (1..100) drives generated squad quality, board expectations, prize
 * money, Champions League seeding and AI transfer behaviour.
 */
object ClubDatabase {

    /**
     * A club seed. [rival] is a club name resolved to an id once every club has
     * been built, so rivalries are stored as real references rather than strings.
     */
    private data class Seed(
        val name: String,
        val short: String,
        val city: String,
        val nickname: String,
        val rep: Int,
        val stadium: String,
        val capacity: Int,
        val primary: Int,
        val secondary: Int,
        val target: Int,
        val shape: BadgeShape,
        val style: Int,
        val rival: String? = null
    )

    // ---------------------------------------------------------------- England

    private val premierLeague = listOf(
        Seed("Manchester Union", "MUN", "Manchester", "The Reds", 94, "Union Park", 74_000,
            0xDA291C, 0xFBE122, 1, BadgeShape.SHIELD, 0, "Manchester Citizens"),
        Seed("Manchester Citizens", "MCI", "Manchester", "The Sky Blues", 93, "Citizens Arena", 53_000,
            0x6CABDD, 0x1C2C5B, 1, BadgeShape.CIRCLE, 1, "Manchester Union"),
        Seed("Merseyside FC", "MER", "Liverpool", "The Reds", 92, "Anfield Heights", 61_000,
            0xC8102E, 0xF6EB61, 2, BadgeShape.PENNANT, 2, "Manchester Union"),
        Seed("Northbank FC", "NBK", "London", "The Gunners", 91, "Ashburton Park", 60_000,
            0xEF0107, 0xFFFFFF, 2, BadgeShape.CREST, 3, "North Tottenham"),
        Seed("Westbridge FC", "WBR", "London", "The Blues", 90, "Bridge Park", 40_000,
            0x034694, 0xDBA111, 3, BadgeShape.SHIELD, 4, "North Tottenham"),
        Seed("North Tottenham", "NTT", "London", "The Spurs", 88, "Whitehart Ground", 62_000,
            0xFFFFFF, 0x132257, 4, BadgeShape.ROUNDEL, 5, "Northbank FC"),
        Seed("Merseyside Blues", "MEB", "Liverpool", "The Toffees", 84, "Goodison Park", 39_000,
            0x003399, 0xFFFFFF, 6, BadgeShape.SHIELD, 0, "Merseyside FC"),
        Seed("Tyneside United", "TYN", "Newcastle", "The Magpies", 85, "St James' Hill", 52_000,
            0x241F20, 0xFFFFFF, 6, BadgeShape.SHIELD, 2, "Wearside FC"),
        Seed("Villa Cross", "VIL", "Birmingham", "The Villans", 83, "Villa Park", 42_000,
            0x95BFE5, 0x670E36, 8, BadgeShape.CREST, 1, "Second City Blues"),
        Seed("Second City Blues", "SCB", "Birmingham", "The Blues", 78, "St Andrew's Field", 29_000,
            0x0000FF, 0xFFFFFF, 12, BadgeShape.CIRCLE, 3, "Villa Cross"),
        Seed("Thames Ironworks", "THI", "London", "The Hammers", 82, "Riverside Stadium", 62_000,
            0x7A263A, 0x1BB1E7, 9, BadgeShape.ROUNDEL, 4, "Westbridge FC"),
        Seed("Wearside FC", "WEA", "Sunderland", "The Black Cats", 76, "Wear Park", 49_000,
            0xEB172B, 0xFFFFFF, 13, BadgeShape.SHIELD, 5, "Tyneside United"),
        Seed("Seaside Albion", "SEA", "Brighton", "The Seagulls", 78, "Coastway Arena", 31_000,
            0x0057B8, 0xFFFFFF, 11, BadgeShape.ROUNDEL, 0, "Solent FC"),
        Seed("Charnwood City", "CHA", "Leicester", "The Foxes", 79, "Charnwood Park", 32_000,
            0x003090, 0xFDBE0F, 10, BadgeShape.CIRCLE, 2, "Trentside Forest"),
        Seed("Molineux FC", "MOL", "Wolverhampton", "The Wolves", 78, "Molineux Ground", 32_000,
            0xFDB913, 0x231F20, 11, BadgeShape.HEXAGON, 3, "Second City Blues"),
        Seed("Selhurst FC", "SEL", "London", "The Eagles", 77, "Selhurst Park", 25_000,
            0x1B458F, 0xC4122B, 12, BadgeShape.CREST, 4, "South London FC"),
        Seed("Trentside Forest", "TRF", "Nottingham", "The Reds", 76, "Trentside Ground", 30_000,
            0xDD0000, 0xFFFFFF, 13, BadgeShape.CIRCLE, 5, "Charnwood City"),
        Seed("Elland United", "ELL", "Leeds", "The Whites", 77, "Elland Road", 37_000,
            0xFFFFFF, 0x1D428A, 12, BadgeShape.SHIELD, 1, "Manchester Union"),
        Seed("Solent FC", "SOL", "Southampton", "The Saints", 75, "Solent Arena", 32_000,
            0xD71920, 0xFFFFFF, 14, BadgeShape.PENNANT, 0, "South Coast FC"),
        Seed("Dean Court FC", "DEA", "Bournemouth", "The Cherries", 73, "Dean Court", 11_000,
            0xDA291C, 0x000000, 15, BadgeShape.CIRCLE, 2, "Solent FC")
    )

    private val championship = listOf(
        Seed("South London FC", "SLO", "London", "The Lions", 68, "The Den", 20_000,
            0x001C58, 0xFFFFFF, 1, BadgeShape.SHIELD, 0, "Selhurst FC"),
        Seed("South Coast FC", "SCO", "Portsmouth", "The Blues", 66, "Fratton Field", 21_000,
            0x001489, 0xFFFFFF, 2, BadgeShape.CREST, 1, "Solent FC"),
        Seed("Norwich Canaries", "NOR", "Norwich", "The Canaries", 67, "Carrow Ground", 27_000,
            0xFFEE00, 0x00A650, 3, BadgeShape.ROUNDEL, 2, "Ipswich Town FC"),
        Seed("Ipswich Town FC", "IPS", "Ipswich", "The Blues", 65, "Portman Field", 29_000,
            0x3A64A3, 0xFFFFFF, 4, BadgeShape.SHIELD, 3, "Norwich Canaries"),
        Seed("Bramall Rovers", "BRA", "Sheffield", "The Blades", 64, "Bramall Lane", 32_000,
            0xEE2737, 0x000000, 6, BadgeShape.PENNANT, 4, "Hillsborough Owls"),
        Seed("Hillsborough Owls", "HIL", "Sheffield", "The Owls", 63, "Hillsborough", 39_000,
            0x0066CC, 0xFFFFFF, 7, BadgeShape.CIRCLE, 5, "Bramall Rovers"),
        Seed("Potteries City", "POT", "Stoke", "The Potters", 64, "Potteries Ground", 30_000,
            0xE03A3E, 0xFFFFFF, 8, BadgeShape.SHIELD, 0, "Trentside Forest"),
        Seed("Coventry Sky", "COV", "Coventry", "The Sky Blues", 62, "Coventry Arena", 32_000,
            0x6CABDD, 0xFFFFFF, 10, BadgeShape.CIRCLE, 1, "Charnwood City"),
        Seed("Watford Hornets", "WAT", "Watford", "The Hornets", 63, "Vicarage Road", 22_000,
            0xFBEE23, 0xED2127, 9, BadgeShape.HEXAGON, 2, "Northbank FC"),
        Seed("Millwall Dockers", "MIL", "London", "The Dockers", 61, "Dock Road", 20_000,
            0x001C58, 0xFFFFFF, 12, BadgeShape.SHIELD, 3, "Westbridge FC"),
        Seed("Preston North", "PRE", "Preston", "The Lilywhites", 60, "Deepdale", 23_000,
            0xFFFFFF, 0x003DA5, 13, BadgeShape.ROUNDEL, 4, "Blackpool Seasiders"),
        Seed("Blackpool Seasiders", "BLA", "Blackpool", "The Seasiders", 58, "Bloomfield Road", 16_000,
            0xFF7A00, 0xFFFFFF, 15, BadgeShape.PENNANT, 5, "Preston North"),
        Seed("Hull Tigers", "HUL", "Hull", "The Tigers", 59, "Hull Arena", 25_000,
            0xF18A00, 0x000000, 14, BadgeShape.CIRCLE, 0, "Elland United"),
        Seed("Bristol Robins", "BRI", "Bristol", "The Robins", 59, "Ashton Park", 27_000,
            0xE21C38, 0xFFFFFF, 14, BadgeShape.SHIELD, 1, "Bristol Pirates"),
        Seed("Bristol Pirates", "BRP", "Bristol", "The Pirates", 57, "Memorial Ground", 12_000,
            0x0060A9, 0xFFFFFF, 16, BadgeShape.CREST, 2, "Bristol Robins"),
        Seed("Swansea Swans", "SWA", "Swansea", "The Swans", 60, "Liberty Park", 21_000,
            0xFFFFFF, 0x000000, 13, BadgeShape.ROUNDEL, 3, "Cardiff Bluebirds"),
        Seed("Cardiff Bluebirds", "CAR", "Cardiff", "The Bluebirds", 59, "Cardiff City Stadium", 33_000,
            0x0070B5, 0xFFFFFF, 14, BadgeShape.SHIELD, 4, "Swansea Swans"),
        Seed("Middlesbrough Iron", "MID", "Middlesbrough", "The Iron", 58, "Riverside Park", 34_000,
            0xE21C38, 0xFFFFFF, 15, BadgeShape.CIRCLE, 5, "Tyneside United"),
        Seed("Derby Rams", "DER", "Derby", "The Rams", 57, "Pride Park", 33_000,
            0xFFFFFF, 0x000000, 16, BadgeShape.HEXAGON, 0, "Trentside Forest"),
        Seed("Reading Royals", "REA", "Reading", "The Royals", 56, "Madejski Park", 24_000,
            0x004494, 0xFFFFFF, 17, BadgeShape.PENNANT, 1, "Watford Hornets")
    )

    // ------------------------------------------------------------------ Spain

    private val laLiga = listOf(
        Seed("Barcelonia FC", "BAR", "Barcelona", "The Blaugrana", 94, "Camp Gran", 99_000,
            0x004D98, 0xA50044, 1, BadgeShape.CREST, 0, "Madrid Blanco"),
        Seed("Madrid Blanco", "MAD", "Madrid", "The Whites", 95, "Estadio Blanco", 81_000,
            0xFFFFFF, 0xFEBE10, 1, BadgeShape.ROUNDEL, 1, "Barcelonia FC"),
        Seed("Atletico Capital", "ATC", "Madrid", "The Colchoneros", 89, "Estadio Metropol", 68_000,
            0xCB3524, 0xFFFFFF, 3, BadgeShape.SHIELD, 2, "Madrid Blanco"),
        Seed("Hispalis FC", "HIS", "Sevilla", "The Rojiblancos", 84, "Estadio Hispalis", 43_000,
            0xFFFFFF, 0xD00000, 5, BadgeShape.CREST, 3, "Betis Verde"),
        Seed("Betis Verde", "BET", "Sevilla", "The Verdiblancos", 82, "Estadio Verdiblanco", 60_000,
            0x00954C, 0xFFFFFF, 6, BadgeShape.ROUNDEL, 4, "Hispalis FC"),
        Seed("Valencia Turia", "VAL", "Valencia", "The Ches", 83, "Estadio Turia", 49_000,
            0xFFFFFF, 0xF18E00, 5, BadgeShape.PENNANT, 5, "Levante Blaugrana"),
        Seed("Submarino FC", "SUB", "Villarreal", "The Yellow Submarine", 82, "Estadio Amarillo", 23_000,
            0xFFE667, 0x005187, 6, BadgeShape.CIRCLE, 0, "Valencia Turia"),
        Seed("San Sebastian Real", "SSR", "San Sebastián", "The Txuri-Urdin", 81, "Estadio Anoeta", 39_000,
            0x0067B1, 0xFFFFFF, 7, BadgeShape.SHIELD, 1, "Bilbao Lions"),
        Seed("Bilbao Lions", "BIL", "Bilbao", "The Lions", 82, "San Mamés Park", 53_000,
            0xEE2523, 0xFFFFFF, 6, BadgeShape.CREST, 2, "San Sebastian Real"),
        Seed("Girona Costa", "GIR", "Girona", "The Blanquivermells", 78, "Estadi Montilivi", 14_000,
            0xCD2534, 0xFFFFFF, 9, BadgeShape.CIRCLE, 3, "Barcelonia FC"),
        Seed("Vigo Celeste", "VIG", "Vigo", "The Celestes", 76, "Balaidos", 29_000,
            0x8AC3EE, 0xFFFFFF, 10, BadgeShape.ROUNDEL, 4, "Deportivo Coruna"),
        Seed("Pamplona Rojos", "PAM", "Pamplona", "The Rojillos", 76, "El Sadar", 23_000,
            0xD91A21, 0x0A346F, 11, BadgeShape.SHIELD, 5, "Bilbao Lions"),
        Seed("Vallecas Rayo", "VLL", "Madrid", "The Lightning", 74, "Estadio de Vallecas", 14_000,
            0xFFFFFF, 0xE53027, 12, BadgeShape.PENNANT, 0, "Madrid Blanco"),
        Seed("Getafe Azulones", "GET", "Getafe", "The Azulones", 73, "Coliseum Azul", 17_000,
            0x005999, 0xFFFFFF, 13, BadgeShape.HEXAGON, 1, "Atletico Capital"),
        Seed("Mallorca Isleño", "MLL", "Palma", "The Islanders", 73, "Son Moix", 23_000,
            0xE20613, 0x000000, 13, BadgeShape.CIRCLE, 2, "Barcelonia FC"),
        Seed("Las Palmas Canario", "LPA", "Las Palmas", "The Canaries", 72, "Estadio Gran Canaria", 32_000,
            0xFFDD00, 0x0055A4, 14, BadgeShape.SHIELD, 3, "Vitoria Alaves"),
        Seed("Vitoria Alaves", "VIT", "Vitoria", "The Babazorros", 71, "Mendizorroza", 19_000,
            0x0067B1, 0xFFFFFF, 15, BadgeShape.CREST, 4, "Bilbao Lions"),
        Seed("Barcelona Periquitos", "BAP", "Barcelona", "The Periquitos", 74, "Estadi Cornella", 40_000,
            0x0067B1, 0xFFFFFF, 12, BadgeShape.ROUNDEL, 5, "Barcelonia FC"),
        Seed("Cadiz Amarillo", "CAD", "Cadiz", "The Yellow Submarine", 70, "Nuevo Mirandilla", 20_000,
            0xFFE500, 0x0055A4, 16, BadgeShape.PENNANT, 0, "Hispalis FC"),
        Seed("Granada Nazari", "GRA", "Granada", "The Nazaries", 70, "Los Carmenes", 19_000,
            0xC4122B, 0xFFFFFF, 16, BadgeShape.CIRCLE, 1, "Hispalis FC")
    )

    // ------------------------------------------------------------------ Italy

    private val serieA = listOf(
        Seed("Torino Bianconeri", "TBI", "Turin", "The Old Lady", 93, "Stadio delle Alpi", 41_000,
            0x000000, 0xFFFFFF, 1, BadgeShape.SHIELD, 0, "Milano Nerazzurri"),
        Seed("Milano Nerazzurri", "MNE", "Milan", "The Nerazzurri", 91, "San Siro Nord", 76_000,
            0x0068A8, 0x000000, 2, BadgeShape.CIRCLE, 1, "Milano Rossoneri"),
        Seed("Milano Rossoneri", "MRO", "Milan", "The Rossoneri", 90, "San Siro Sud", 76_000,
            0xFB090B, 0x000000, 2, BadgeShape.ROUNDEL, 2, "Milano Nerazzurri"),
        Seed("Napoli Vesuvio", "NAP", "Naples", "The Partenopei", 88, "Stadio Vesuvio", 55_000,
            0x12A0D7, 0xFFFFFF, 3, BadgeShape.SHIELD, 3, "Roma Capitolina"),
        Seed("Roma Capitolina", "ROM", "Rome", "The Giallorossi", 86, "Stadio Olimpico Sud", 70_000,
            0x8E1F2F, 0xF0BC3C, 4, BadgeShape.CREST, 4, "Lazio Aquile"),
        Seed("Lazio Aquile", "LAZ", "Rome", "The Eagles", 84, "Stadio Olimpico Nord", 70_000,
            0x87D8F7, 0xFFFFFF, 5, BadgeShape.PENNANT, 5, "Roma Capitolina"),
        Seed("Atalanta Orobica", "ATA", "Bergamo", "The Orobici", 84, "Gewiss Arena", 25_000,
            0x1E71B8, 0x000000, 4, BadgeShape.CIRCLE, 0, "Bologna Rossoblu"),
        Seed("Fiorentina Viola", "FIO", "Florence", "The Viola", 82, "Stadio Franchi", 43_000,
            0x592C82, 0xFFFFFF, 6, BadgeShape.SHIELD, 0, "Torino Bianconeri"),
        Seed("Torino Granata", "TGR", "Turin", "The Granata", 79, "Stadio Filadelfia", 28_000,
            0x8B1A1A, 0xFFFFFF, 8, BadgeShape.CIRCLE, 1, "Torino Bianconeri"),
        Seed("Bologna Rossoblu", "BOL", "Bologna", "The Rossoblu", 79, "Stadio Dall'Ara", 38_000,
            0xA21C2C, 0x1C2C5B, 8, BadgeShape.ROUNDEL, 2, "Fiorentina Viola"),
        Seed("Genoa Grifone", "GEN", "Genoa", "The Griffin", 77, "Stadio Marassi", 36_000,
            0xA21C2C, 0x1C2C5B, 10, BadgeShape.CREST, 3, "Sassuolo Neroverdi"),
        Seed("Udine Friuli", "UDI", "Udine", "The Zebrette", 75, "Stadio Friuli", 25_000,
            0x000000, 0xFFFFFF, 11, BadgeShape.PENNANT, 4, "Verona Scaligera"),
        Seed("Sassuolo Neroverdi", "SAS", "Sassuolo", "The Neroverdi", 74, "Mapei Arena", 21_000,
            0x00A752, 0x000000, 12, BadgeShape.HEXAGON, 5, "Bologna Rossoblu"),
        Seed("Verona Scaligera", "VER", "Verona", "The Scaligeri", 74, "Stadio Bentegodi", 39_000,
            0xF5D800, 0x0A2F6B, 12, BadgeShape.SHIELD, 0, "Udine Friuli"),
        Seed("Cagliari Isolani", "CAG", "Cagliari", "The Isolani", 73, "Unipol Arena", 16_000,
            0xA21C2C, 0x1C2C5B, 13, BadgeShape.CIRCLE, 1, "Napoli Vesuvio"),
        Seed("Empoli Azzurri", "EMP", "Empoli", "The Azzurri", 71, "Stadio Castellani", 16_000,
            0x12A0D7, 0xFFFFFF, 15, BadgeShape.ROUNDEL, 2, "Fiorentina Viola"),
        Seed("Lecce Salento", "LEC", "Lecce", "The Salentini", 72, "Stadio Via del Mare", 31_000,
            0xF5D800, 0xA21C2C, 14, BadgeShape.CREST, 3, "Napoli Vesuvio"),
        Seed("Monza Brianza", "MON", "Monza", "The Brianzoli", 71, "U-Power Arena", 18_000,
            0xC4122B, 0xFFFFFF, 15, BadgeShape.PENNANT, 4, "Milano Rossoneri"),
        Seed("Salerno Granata", "SAL", "Salerno", "The Granata", 70, "Stadio Arechi", 37_000,
            0x8B1A1A, 0xFFFFFF, 16, BadgeShape.HEXAGON, 5, "Napoli Vesuvio"),
        Seed("Frosinone Ciociari", "FRO", "Frosinone", "The Ciociari", 69, "Stadio Stirpe", 16_000,
            0xF5D800, 0x0A2F6B, 17, BadgeShape.CIRCLE, 0, "Roma Capitolina")
    )

    // ---------------------------------------------------------------- Germany

    private val bundesliga = listOf(
        Seed("Bavaria Munchen", "BAV", "Munich", "The Bavarians", 94, "Allianz Arena", 75_000,
            0xDC052D, 0xFFFFFF, 1, BadgeShape.ROUNDEL, 0, "Dortmund Westfalen"),
        Seed("Dortmund Westfalen", "DOR", "Dortmund", "The Yellow Wall", 89, "Westfalenstadion", 81_000,
            0xFDE100, 0x000000, 2, BadgeShape.CIRCLE, 1, "Bavaria Munchen"),
        Seed("Leipzig Sachsen", "LEI", "Leipzig", "The Bulls", 86, "Sachsen Arena", 47_000,
            0xFFFFFF, 0xDD0741, 3, BadgeShape.SHIELD, 2, "Dortmund Westfalen"),
        Seed("Leverkusen Rhein", "LEV", "Leverkusen", "The Werkself", 86, "BayArena", 30_000,
            0xE32221, 0x000000, 3, BadgeShape.CIRCLE, 3, "Koln Dom"),
        Seed("Frankfurt Adler", "FRA", "Frankfurt", "The Eagles", 82, "Waldstadion", 58_000,
            0xE1000F, 0x000000, 5, BadgeShape.PENNANT, 4, "Mainz Rheinland"),
        Seed("Stuttgart Neckar", "STU", "Stuttgart", "The Swabians", 80, "Neckarstadion", 60_000,
            0xFFFFFF, 0xE32219, 6, BadgeShape.CREST, 5, "Bavaria Munchen"),
        Seed("Freiburg Schwarzwald", "FRB", "Freiburg", "The Black Forest", 77, "Schwarzwaldstadion", 34_000,
            0xE1000F, 0x000000, 8, BadgeShape.SHIELD, 0, "Stuttgart Neckar"),
        Seed("Wolfsburg Autostadt", "WOL", "Wolfsburg", "The Wolves", 76, "Volkswagen Arena", 30_000,
            0x65B32E, 0xFFFFFF, 9, BadgeShape.CIRCLE, 1, "Hannover Leine"),
        Seed("Mainz Rheinland", "MAI", "Mainz", "The Nullfunfer", 74, "Mewa Arena", 33_000,
            0xC31417, 0xFFFFFF, 11, BadgeShape.SHIELD, 2, "Frankfurt Adler"),
        Seed("Augsburg Schwaben", "AUG", "Augsburg", "The Fuggerstadter", 73, "WWK Arena", 30_000,
            0xBA3733, 0xFFFFFF, 12, BadgeShape.HEXAGON, 3, "Bavaria Munchen"),
        Seed("Bremen Weser", "BRE", "Bremen", "The Green-Whites", 75, "Weserstadion", 42_000,
            0x1D9053, 0xFFFFFF, 10, BadgeShape.SHIELD, 0, "Hamburg Rothosen"),
        Seed("Hoffenheim Kraichgau", "HOF", "Sinsheim", "The Kraichgauer", 72, "PreZero Arena", 30_000,
            0x1C63B7, 0xFFFFFF, 13, BadgeShape.CIRCLE, 1, "Stuttgart Neckar"),
        Seed("Union Berliner", "UNB", "Berlin", "The Iron Ones", 73, "Alte Forsterei", 22_000,
            0xEB1923, 0xFFFFFF, 12, BadgeShape.ROUNDEL, 2, "Hannover Leine"),
        Seed("Bochum Ruhr", "BOC", "Bochum", "The Unrelegables", 70, "Ruhrstadion", 26_000,
            0x005CA9, 0xFFFFFF, 15, BadgeShape.CREST, 3, "Dortmund Westfalen"),
        Seed("Heidenheim Ostalb", "HEI", "Heidenheim", "The Ostalb", 69, "Voith-Arena", 15_000,
            0x003D7C, 0xE1000F, 16, BadgeShape.PENNANT, 4, "Augsburg Schwaben"),
        Seed("Koln Dom", "KOL", "Cologne", "The Billy Goats", 74, "RheinEnergie", 50_000,
            0xE1000F, 0xFFFFFF, 11, BadgeShape.HEXAGON, 5, "Gladbach Borussen"),
        Seed("Gladbach Borussen", "GLA", "Monchengladbach", "The Foals", 76, "Borussia-Park", 54_000,
            0xFFFFFF, 0x000000, 9, BadgeShape.SHIELD, 4, "Koln Dom"),
        Seed("Hannover Leine", "HAN", "Hannover", "The Reds", 72, "Niedersachsenstadion", 49_000,
            0x1D9053, 0x000000, 13, BadgeShape.CIRCLE, 5, "Wolfsburg Autostadt")
    )

    // ----------------------------------------------------------------- France

    private val ligue1 = listOf(
        Seed("Paris Royale", "PAR", "Paris", "The Parisians", 93, "Parc des Princes", 48_000,
            0x004170, 0xDA291C, 1, BadgeShape.ROUNDEL, 0, "Marseille Phocea"),
        Seed("Marseille Phocea", "MAR", "Marseille", "The Phocaeans", 86, "Stade Velodrome", 67_000,
            0x2FAEE0, 0xFFFFFF, 3, BadgeShape.CIRCLE, 1, "Paris Royale"),
        Seed("Lyonnaise Rhone", "LYO", "Lyon", "The Kids", 84, "Groupama Park", 59_000,
            0xFFFFFF, 0xDA001A, 4, BadgeShape.SHIELD, 2, "Saint-Etienne Verts"),
        Seed("Monaco Azur", "MON", "Monaco", "The Red and Whites", 84, "Stade Louis II", 18_000,
            0xE63946, 0xFFFFFF, 4, BadgeShape.PENNANT, 3, "Nice Cote d'Azur"),
        Seed("Lille Nord", "LIL", "Lille", "The Dogues", 81, "Stade Pierre-Mauroy", 50_000,
            0xE01E13, 0x003DA5, 6, BadgeShape.CIRCLE, 4, "Lens Artois"),
        Seed("Rennes Bretagne", "REN", "Rennes", "The Rouge et Noir", 79, "Roazhon Park", 30_000,
            0xE23029, 0x000000, 7, BadgeShape.SHIELD, 5, "Nantes Atlantique"),
        Seed("Nice Cote d'Azur", "NIC", "Nice", "The Aiglons", 77, "Allianz Riviera", 35_000,
            0xDA291C, 0x000000, 9, BadgeShape.CREST, 0, "Monaco Azur"),
        Seed("Lens Artois", "LEN", "Lens", "The Blood and Gold", 77, "Stade Bollaert", 38_000,
            0xFDD500, 0xD00000, 9, BadgeShape.HEXAGON, 1, "Lille Nord"),
        Seed("Strasbourg Alsace", "STR", "Strasbourg", "The Racers", 74, "Stade de la Meinau", 29_000,
            0x005BAA, 0xFFFFFF, 12, BadgeShape.SHIELD, 2, "Metz Lorraine"),
        Seed("Toulouse Garonne", "TOU", "Toulouse", "The Violets", 73, "Stadium de Toulouse", 33_000,
            0x592C82, 0xFFFFFF, 13, BadgeShape.CIRCLE, 3, "Montpellier Herault"),
        Seed("Montpellier Herault", "MTP", "Montpellier", "The Paladins", 72, "Stade Mosson", 32_000,
            0xF47B20, 0x005BAA, 14, BadgeShape.SHIELD, 4, "Toulouse Garonne"),
        Seed("Nantes Atlantique", "NAN", "Nantes", "The Canaries", 73, "Stade Beaujoire", 37_000,
            0xFDD500, 0x1D9053, 13, BadgeShape.PENNANT, 5, "Rennes Bretagne"),
        Seed("Brest Finistere", "BRF", "Brest", "The Pirates", 72, "Stade Francis-Le Ble", 16_000,
            0xD00000, 0xFFFFFF, 14, BadgeShape.CIRCLE, 0, "Rennes Bretagne"),
        Seed("Reims Champagne", "REI", "Reims", "The Champagne", 71, "Stade Auguste-Delaune", 21_000,
            0xD00000, 0xFFFFFF, 15, BadgeShape.SHIELD, 1, "Paris Royale"),
        Seed("Auxerre Bourgogne", "AUX", "Auxerre", "The Burgundians", 70, "Stade Abbe-Deschamps", 18_000,
            0x005BAA, 0xFFFFFF, 16, BadgeShape.CREST, 2, "Paris Royale"),
        Seed("Le Havre Normandie", "HAV", "Le Havre", "The Sky and Navy", 70, "Stade Oceane", 25_000,
            0x005BAA, 0x8AC3EE, 16, BadgeShape.SHIELD, 3, "Rennes Bretagne"),
        Seed("Saint-Etienne Verts", "STE", "Saint-Etienne", "The Greens", 75, "Stade Geoffroy-Guichard", 42_000,
            0x1D9053, 0xFFFFFF, 11, BadgeShape.ROUNDEL, 4, "Lyonnaise Rhone"),
        Seed("Metz Lorraine", "MET", "Metz", "The Grenats", 69, "Stade Saint-Symphorien", 30_000,
            0x8B1A1A, 0xFFFFFF, 17, BadgeShape.CIRCLE, 5, "Strasbourg Alsace")
    )

    // --------------------------------------------------------------- Portugal

    private val primeiraLiga = listOf(
        Seed("Porto Dragoes", "POR", "Porto", "The Dragons", 88, "Estadio do Dragao", 50_000,
            0x003DA5, 0xFFFFFF, 2, BadgeShape.SHIELD, 0, "Lisboa Aguias"),
        Seed("Lisboa Aguias", "LIS", "Lisbon", "The Eagles", 89, "Estadio da Luz", 65_000,
            0xFF0000, 0xFFFFFF, 2, BadgeShape.CREST, 1, "Porto Dragoes"),
        Seed("Lisboa Leoes", "LLE", "Lisbon", "The Lions", 88, "Estadio Jose Alvalade", 50_000,
            0x00A650, 0xFFFFFF, 2, BadgeShape.CIRCLE, 2, "Porto Dragoes"),
        Seed("Braga Arsenalistas", "BRG", "Braga", "The Arsenalistas", 82, "Estadio Municipal", 30_000,
            0xD00000, 0xFFFFFF, 4, BadgeShape.SHIELD, 3, "Guimaraes Vitoria"),
        Seed("Guimaraes Vitoria", "GUI", "Guimaraes", "The Conquerors", 78, "Estadio D. Afonso", 30_000,
            0xFFFFFF, 0x000000, 6, BadgeShape.CIRCLE, 4, "Braga Arsenalistas"),
        Seed("Coimbra Briosa", "COI", "Coimbra", "The Students", 74, "Estadio Cidade", 30_000,
            0x000000, 0xFFFFFF, 9, BadgeShape.PENNANT, 5, "Braga Arsenalistas"),
        Seed("Famalicao Vila", "FAM", "Famalicao", "The Vila Nova", 72, "Estadio Municipal", 5_000,
            0x003DA5, 0xFFFFFF, 11, BadgeShape.SHIELD, 0, "Braga Arsenalistas"),
        Seed("Portimao Algarve", "PTI", "Portimao", "The Algarve", 71, "Estadio Algarve", 6_000,
            0x000000, 0xFFFFFF, 12, BadgeShape.CIRCLE, 1, "Setubal Sado"),
        Seed("Faro Sea", "FAR", "Faro", "The Sea", 68, "Estadio Algarve Sul", 7_000,
            0x00A650, 0xFFFFFF, 14, BadgeShape.ROUNDEL, 2, "Portimao Algarve"),
        Seed("Aves Rio", "AVE", "Aves", "The River", 67, "Estadio do Rio", 5_000,
            0xD00000, 0xFFFFFF, 15, BadgeShape.SHIELD, 3, "Porto Dragoes"),
        Seed("Setubal Sado", "SET", "Setubal", "The Sado", 70, "Estadio do Bonfim", 18_000,
            0x00A650, 0xFFFFFF, 13, BadgeShape.CIRCLE, 4, "Lisboa Aguias"),
        Seed("Estoril Costa", "EST", "Estoril", "The Coast", 70, "Estadio Antonio Coimbra", 8_000,
            0xFDD500, 0x003DA5, 13, BadgeShape.HEXAGON, 5, "Lisboa Leoes"),
        Seed("Vizela Douro", "VIZ", "Vizela", "The Douro", 66, "Estadio do Douro", 6_000,
            0x003DA5, 0xFFFFFF, 16, BadgeShape.SHIELD, 0, "Guimaraes Vitoria"),
        Seed("Chaves Flaviense", "CHV", "Chaves", "The Flaviense", 66, "Estadio Municipal", 8_000,
            0xD00000, 0xFFFFFF, 16, BadgeShape.CIRCLE, 1, "Braga Arsenalistas"),
        Seed("Moreirense Verdes", "MOR", "Moreira", "The Greens", 67, "Comendador Joaquim", 6_000,
            0x00A650, 0xFFFFFF, 15, BadgeShape.ROUNDEL, 2, "Guimaraes Vitoria"),
        Seed("Gil Vicente Galos", "GIL", "Barcelos", "The Roosters", 68, "Estadio Cidade de Barcelos", 12_000,
            0xD00000, 0xFFFFFF, 14, BadgeShape.PENNANT, 3, "Braga Arsenalistas"),
        Seed("Rio Ave Vilacondense", "RVA", "Vila do Conde", "The Vilacondense", 66, "Estadio dos Arcos", 5_000,
            0x00A650, 0xFFFFFF, 16, BadgeShape.SHIELD, 4, "Porto Dragoes"),
        Seed("Arouca Serra", "ARO", "Arouca", "The Serra", 66, "Estadio Municipal", 5_000,
            0xFDD500, 0x000000, 16, BadgeShape.CIRCLE, 5, "Porto Dragoes")
    )

    // ------------------------------------------------------------ Netherlands

    private val eredivisie = listOf(
        Seed("Amsterdam Godenzonen", "AMS", "Amsterdam", "The Sons of Gods", 87, "Johan Arena", 55_000,
            0xD21217, 0xFFFFFF, 1, BadgeShape.ROUNDEL, 0, "Eindhoven Lampen"),
        Seed("Eindhoven Lampen", "EIN", "Eindhoven", "The Lightbulbs", 86, "Philips Stadion", 35_000,
            0xE4002B, 0xFFFFFF, 2, BadgeShape.SHIELD, 1, "Amsterdam Godenzonen"),
        Seed("Rotterdam Legioen", "ROT", "Rotterdam", "The Legion", 84, "De Kuip", 51_000,
            0xDA291C, 0x000000, 3, BadgeShape.CIRCLE, 2, "Amsterdam Godenzonen"),
        Seed("Alkmaar Kaasboeren", "ALK", "Alkmaar", "The Cheese Farmers", 79, "AFAS Stadion", 19_000,
            0xE4002B, 0xFFFFFF, 5, BadgeShape.PENNANT, 3, "Amsterdam Godenzonen"),
        Seed("Utrecht Domstad", "UTR", "Utrecht", "The Dom City", 78, "Stadion Galgenwaard", 23_000,
            0xD21217, 0xFFFFFF, 6, BadgeShape.SHIELD, 4, "Amsterdam Godenzonen"),
        Seed("Twente Tukkers", "TWE", "Enschede", "The Tukkers", 78, "De Grolsch Veste", 30_000,
            0xE4002B, 0xFFFFFF, 6, BadgeShape.CIRCLE, 5, "Heerenveen Friezen"),
        Seed("Arnhem Vitesse", "ARN", "Arnhem", "The Yellow-Blacks", 75, "GelreDome", 21_000,
            0xFDD500, 0x000000, 8, BadgeShape.ROUNDEL, 0, "Nijmegen Eagles"),
        Seed("Nijmegen Eagles", "NIJ", "Nijmegen", "The Eagles", 72, "Goffertstadion", 12_000,
            0xD21217, 0x000000, 11, BadgeShape.SHIELD, 1, "Arnhem Vitesse"),
        Seed("Heerenveen Friezen", "HEE", "Heerenveen", "The Frisians", 72, "Abe Lenstra Stadion", 27_000,
            0x005CA9, 0xFFFFFF, 11, BadgeShape.CIRCLE, 2, "Twente Tukkers"),
        Seed("Groningen Trots", "GRO", "Groningen", "The Pride of the North", 73, "Euroborg", 22_000,
            0x00A650, 0xFFFFFF, 10, BadgeShape.SHIELD, 3, "Heerenveen Friezen"),
        Seed("Breda Parel", "BRD", "Breda", "The Pearl", 71, "Rat Verlegh Stadion", 19_000,
            0xD21217, 0xFFFFFF, 12, BadgeShape.PENNANT, 4, "Tilburg Tricolores"),
        Seed("Tilburg Tricolores", "TIL", "Tilburg", "The Tricolores", 70, "Koning Willem II Stadion", 14_000,
            0x005CA9, 0xFDD500, 13, BadgeShape.SHIELD, 5, "Breda Parel"),
        Seed("Rotterdam Sparta", "RSP", "Rotterdam", "The Castle Lords", 69, "Het Kasteel", 11_000,
            0xD21217, 0xFFFFFF, 14, BadgeShape.CIRCLE, 0, "Rotterdam Legioen"),
        Seed("Sittard Fortunezen", "SIT", "Sittard", "The Fortuna", 68, "Fortuna Sittard Stadion", 12_000,
            0xFDD500, 0x00A650, 15, BadgeShape.ROUNDEL, 1, "Heerenveen Friezen"),
        Seed("Almere Polder", "ALM", "Almere", "The Polder", 67, "Yanmar Stadion", 4_000,
            0xD21217, 0x000000, 16, BadgeShape.SHIELD, 2, "Utrecht Domstad"),
        Seed("Zwolle Blauwvingers", "ZWO", "Zwolle", "The Blue Fingers", 69, "MAC3PARK Stadion", 13_000,
            0x005CA9, 0xFFFFFF, 14, BadgeShape.CIRCLE, 3, "Groningen Trots"),
        Seed("Waalwijk RKC", "WAA", "Waalwijk", "The Yellow-Blues", 67, "Mandemakers Stadion", 7_000,
            0xFDD500, 0x005CA9, 16, BadgeShape.HEXAGON, 4, "Breda Parel"),
        Seed("Doetinchem Graafschap", "DOE", "Doetinchem", "The Superfarmers", 67, "De Vijverberg", 12_000,
            0x005CA9, 0xFFFFFF, 16, BadgeShape.SHIELD, 5, "Arnhem Vitesse")
    )

    private val byLeague: Map<String, List<Seed>> = mapOf(
        League.PREMIER_LEAGUE.id to premierLeague,
        League.CHAMPIONSHIP.id to championship,
        League.LA_LIGA.id to laLiga,
        League.SERIE_A.id to serieA,
        League.BUNDESLIGA.id to bundesliga,
        League.LIGUE_1.id to ligue1,
        League.PRIMEIRA.id to primeiraLiga,
        League.EREDIVISIE.id to eredivisie
    )

    /** Total clubs in the starting database. */
    val clubCount: Int = byLeague.values.sumOf { it.size }

    /**
     * Builds every club. Budgets are derived from reputation so a top club can
     * spend like a top club while a promoted side must be frugal, and rivalries
     * are resolved into real club ids.
     */
    fun buildAll(): List<Club> {
        val clubs = mutableListOf<Club>()
        val idByName = mutableMapOf<String, Long>()
        var id = 1L

        // First pass: assign ids so rivalries can be resolved to real references.
        for (league in League.all) {
            val seeds = byLeague[league.id] ?: continue
            for (seed in seeds) {
                idByName[seed.name] = id++
            }
        }

        id = 1L
        for (league in League.all) {
            val seeds = byLeague[league.id] ?: continue
            for (seed in seeds) {
                val transferBudget = FinanceModel.transferBudgetFor(seed.rep, league.tier)
                val wageBudget = FinanceModel.wageBudgetFor(seed.rep)
                val balance = FinanceModel.openingBalance(seed.rep, league.tier)

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
                    formationId = defaultFormationFor(seed.rep),
                    city = seed.city,
                    nickname = seed.nickname,
                    badgeShape = seed.shape,
                    badgeStyle = seed.style,
                    rivalClubId = seed.rival?.let { idByName[it] }
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
    private fun defaultFormationFor(reputation: Int): String = when {
        reputation >= 86 -> Formation.F433.id
        reputation >= 78 -> Formation.F4231.id
        reputation >= 70 -> Formation.F4231.id
        reputation >= 62 -> Formation.F442.id
        else -> Formation.F532.id
    }
}
