package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.PreferredFoot

/**
 * Curated squads for a selection of the game's biggest clubs.
 *
 * Every entry is a **fictionalised** player. The squad *shape* — positions,
 * ages, ability and potential — is inspired by real-world sides so the database
 * feels authentic, but each surname is invented: no entry reproduces a real
 * person's name, and no photograph, likeness or club crest is used or implied.
 * These are original characters that merely evoke the real football landscape.
 *
 * The values here are the *design* for a squad — position, current ability,
 * potential, age and preferred foot. Attributes are derived from these by
 * [CuratedSquadBuilder] using the same position profiles as the generated
 * database, so curated players are internally consistent with the rest of the
 * world (their displayed overall always matches their attributes).
 */
object CuratedSquads {

    data class CuratedPlayer(
        val name: String,
        val position: Position,
        val overall: Int,
        val potential: Int,
        val age: Int,
        val foot: PreferredFoot = PreferredFoot.RIGHT
    )

    private val R = PreferredFoot.RIGHT
    private val L = PreferredFoot.LEFT
    private val B = PreferredFoot.BOTH

    /**
     * Club name (exactly as it appears in [ClubDatabase]) -> curated squad.
     * Clubs not present here keep the fully generated squad.
     */
    val byClubName: Map<String, List<CuratedPlayer>> = mapOf(

        // ------------------------------------------------------------ Spain
        "Madrid Blanco" to listOf(
            CuratedPlayer("Thibault Courbet", Position.GK, 89, 89, 34, R),
            CuratedPlayer("Andriy Lunyk", Position.GK, 80, 84, 27, R),
            CuratedPlayer("Diego Herrera", Position.GK, 68, 76, 21, R),
            CuratedPlayer("Raúl Asens", Position.CB, 79, 87, 22, R),
            CuratedPlayer("Éder Milhazes", Position.CB, 84, 86, 28, R),
            CuratedPlayer("Dean Huisman", Position.CB, 80, 89, 21, R),
            CuratedPlayer("Anton Rüdinger", Position.CB, 84, 84, 33, R),
            CuratedPlayer("Trey Alexandersson", Position.RB, 86, 88, 27, R),
            CuratedPlayer("Denzel Dumont", Position.RB, 82, 84, 30, R),
            CuratedPlayer("Marc Cucerán", Position.LB, 82, 84, 28, L),
            CuratedPlayer("Álvaro Carreño", Position.LB, 79, 85, 23, L),
            CuratedPlayer("Ferland Mendès", Position.LB, 82, 83, 31, L),
            CuratedPlayer("Ibrahima Koné", Position.CB, 84, 87, 27, R),
            CuratedPlayer("Jude Belmore", Position.CAM, 88, 92, 23, R),
            CuratedPlayer("Eduardo Camara", Position.CM, 84, 89, 23, L),
            CuratedPlayer("Federico Valdés", Position.CM, 87, 88, 28, R),
            CuratedPlayer("Aurélien Tchami", Position.CDM, 84, 88, 26, R),
            CuratedPlayer("Arda Güney", Position.CAM, 82, 90, 21, L),
            CuratedPlayer("Bernardo Silveira", Position.CM, 86, 86, 32, L),
            CuratedPlayer("Tiago Almeida", Position.CM, 82, 82, 35, R),
            CuratedPlayer("Vinícius Rocha", Position.LW, 90, 92, 26, R),
            CuratedPlayer("Endrico Sousa", Position.ST, 79, 89, 20, R),
            CuratedPlayer("Kylian Mbaye", Position.ST, 91, 92, 27, R),
            CuratedPlayer("Rodrigo Goulart", Position.RW, 85, 87, 25, L),
            CuratedPlayer("Carlos Espinar", Position.ST, 74, 85, 21, R),
            CuratedPlayer("Brahim Diallo", Position.RW, 80, 82, 27, R),
            CuratedPlayer("Yan Diarra", Position.LW, 75, 86, 20, R)
        ),

        "Barcelonia FC" to listOf(
            CuratedPlayer("Joan Garriga", Position.GK, 82, 87, 25, R),
            CuratedPlayer("Wojciech Szczesniak", Position.GK, 80, 80, 36, R),
            CuratedPlayer("Dominik Livaja", Position.GK, 79, 81, 31, R),
            CuratedPlayer("Diego Koemans", Position.GK, 66, 78, 20, R),
            CuratedPlayer("João Cancelas", Position.RB, 82, 84, 32, R),
            CuratedPlayer("Aleix Balde", Position.LB, 82, 85, 23, L),
            CuratedPlayer("Pau Cubells", Position.CB, 84, 90, 19, R),
            CuratedPlayer("Andreas Kristensen", Position.CB, 80, 80, 30, R),
            CuratedPlayer("Gerard Martí", Position.LB, 76, 80, 24, L),
            CuratedPlayer("Jules Kondé", Position.CB, 85, 87, 28, R),
            CuratedPlayer("Eric Garcés", Position.CB, 79, 82, 26, R),
            CuratedPlayer("Xavi Esparta", Position.RB, 72, 82, 19, R),
            CuratedPlayer("Gavi Rueda", Position.CM, 83, 89, 22, R),
            CuratedPlayer("Fermín Lópera", Position.CAM, 82, 88, 23, R),
            CuratedPlayer("Pedri Ferrán", Position.CM, 87, 90, 24, R),
            CuratedPlayer("Rodrigo Sáez", Position.CDM, 88, 88, 30, R),
            CuratedPlayer("Frenkie de Jonge", Position.CM, 84, 85, 29, R),
            CuratedPlayer("Marc Bernat", Position.CDM, 76, 86, 19, R),
            CuratedPlayer("Brian Fariñes", Position.CM, 74, 84, 19, L),
            CuratedPlayer("Dani Olmedo", Position.CAM, 84, 85, 28, L),
            CuratedPlayer("Lamine Yamasal", Position.RW, 87, 94, 19, L),
            CuratedPlayer("Raphael Dias", Position.LW, 86, 87, 30, L),
            CuratedPlayer("Gabriel Júnior", Position.ST, 81, 82, 29, R),
            CuratedPlayer("Karim Adeyelo", Position.LW, 81, 85, 24, L),
            CuratedPlayer("Anthony Gorton", Position.LW, 81, 84, 25, L),
            CuratedPlayer("Roony Bardhøj", Position.RW, 76, 86, 21, L),
            CuratedPlayer("Hamza Abdel", Position.CAM, 73, 84, 19, R),
            CuratedPlayer("Jesse Bisiwu", Position.RW, 72, 83, 20, L)
        ),

        "Atletico Capital" to listOf(
            CuratedPlayer("Jan Oblek", Position.GK, 87, 87, 34, R),
            CuratedPlayer("Juan Mussot", Position.GK, 78, 79, 32, R),
            CuratedPlayer("José Gimena", Position.CB, 84, 84, 31, R),
            CuratedPlayer("Robin Lenormand", Position.CB, 83, 84, 30, R),
            CuratedPlayer("Nahuel Molinas", Position.RB, 81, 83, 28, R),
            CuratedPlayer("Reinildo Mandavas", Position.LB, 80, 81, 32, L),
            CuratedPlayer("César Azpil", Position.RB, 78, 78, 37, R),
            CuratedPlayer("Axel Witson", Position.CB, 78, 78, 37, R),
            CuratedPlayer("Clément Lenglois", Position.CB, 79, 80, 31, L),
            CuratedPlayer("Jorge Resina", Position.CM, 83, 83, 34, R),
            CuratedPlayer("Rodrigo Depaulo", Position.CM, 82, 82, 32, R),
            CuratedPlayer("Marcos Llorens", Position.RW, 82, 83, 31, R),
            CuratedPlayer("Conor Gallaghy", Position.CM, 80, 83, 26, R),
            CuratedPlayer("Pablo Barriento", Position.CM, 79, 86, 23, R),
            CuratedPlayer("Samuel Linos", Position.LW, 80, 83, 26, L),
            CuratedPlayer("Rodrigo Riquelmes", Position.LW, 78, 82, 26, L),
            CuratedPlayer("Thomas Lemard", Position.CAM, 78, 78, 30, L),
            CuratedPlayer("Antoine Grimard", Position.CAM, 86, 86, 35, L),
            CuratedPlayer("Julián Alvero", Position.ST, 85, 88, 26, R),
            CuratedPlayer("Alexander Sørlin", Position.ST, 82, 83, 30, R),
            CuratedPlayer("Ángel Corredo", Position.RW, 80, 80, 31, R),
            CuratedPlayer("Giuliano Simoni", Position.RW, 78, 84, 23, R)
        ),

        // ---------------------------------------------------------- England
        "Manchester Citizens" to listOf(
            CuratedPlayer("Gianluigi Donnaruma", Position.GK, 88, 89, 27, R),
            CuratedPlayer("Gerónimo Rullio", Position.GK, 79, 80, 34, R),
            CuratedPlayer("Marcus Bettinello", Position.GK, 72, 72, 34, R),
            CuratedPlayer("Rúben Diano", Position.CB, 87, 88, 29, R),
            CuratedPlayer("Marc Guého", Position.CB, 83, 86, 26, R),
            CuratedPlayer("Joško Gvardic", Position.CB, 84, 88, 24, L),
            CuratedPlayer("Rico Lewin", Position.RB, 79, 85, 21, R),
            CuratedPlayer("Rayan Aït-Nouro", Position.LB, 81, 84, 25, L),
            CuratedPlayer("Vitor Reiso", Position.CB, 76, 86, 20, R),
            CuratedPlayer("Abdukodir Khusano", Position.CB, 78, 86, 22, R),
            CuratedPlayer("Kaden Braithwait", Position.CB, 70, 82, 19, R),
            CuratedPlayer("Josh Wilsonby", Position.LB, 71, 80, 23, L),
            CuratedPlayer("Phil Fodin", Position.CAM, 86, 89, 26, L),
            CuratedPlayer("Enzo Fernán", Position.CM, 85, 88, 25, R),
            CuratedPlayer("Matheus Nunez", Position.CM, 81, 83, 27, R),
            CuratedPlayer("Mateo Kovačin", Position.CM, 83, 83, 32, R),
            CuratedPlayer("Rayan Cherko", Position.CAM, 80, 87, 22, L),
            CuratedPlayer("Elliot Anders", Position.CM, 78, 84, 23, R),
            CuratedPlayer("Ayyoub Bouadi", Position.CM, 74, 86, 19, R),
            CuratedPlayer("Nico O. Reily", Position.CM, 74, 84, 21, L),
            CuratedPlayer("Floyd Samba", Position.CDM, 70, 82, 20, R),
            CuratedPlayer("Erling Halland", Position.ST, 91, 93, 26, L),
            CuratedPlayer("Jérémy Dokou", Position.LW, 83, 86, 24, R),
            CuratedPlayer("Iliman Ndiayo", Position.LW, 80, 83, 26, R),
            CuratedPlayer("Antoine Semeny", Position.RW, 80, 83, 26, R),
            CuratedPlayer("Allan Elias", Position.ST, 72, 83, 20, R),
            CuratedPlayer("Ryan McAidoo", Position.RW, 71, 84, 19, L)
        ),

        "Manchester Union" to listOf(
            CuratedPlayer("Senne Lammerts", Position.GK, 79, 86, 24, R),
            CuratedPlayer("Karl Darlowe", Position.GK, 74, 74, 35, R),
            CuratedPlayer("Tom Heatherton", Position.GK, 72, 72, 40, R),
            CuratedPlayer("Lisandro Martínes", Position.CB, 83, 84, 28, L),
            CuratedPlayer("Matthijs de Ligter", Position.CB, 83, 84, 27, R),
            CuratedPlayer("Leny Yorot", Position.CB, 79, 89, 20, R),
            CuratedPlayer("Diogo Dalos", Position.RB, 82, 83, 27, R),
            CuratedPlayer("Noussair Mazra", Position.RB, 81, 82, 28, R),
            CuratedPlayer("Lucas Shaw", Position.LB, 80, 80, 31, L),
            CuratedPlayer("Harvey Maguire", Position.CB, 79, 79, 33, R),
            CuratedPlayer("Ayden Havers", Position.CB, 72, 84, 19, R),
            CuratedPlayer("Patrick Dorgus", Position.LB, 76, 84, 22, L),
            CuratedPlayer("Bruno Ferrão", Position.CAM, 86, 86, 32, R),
            CuratedPlayer("Kobbie Mains", Position.CM, 80, 88, 21, R),
            CuratedPlayer("Manuel Ugartes", Position.CDM, 80, 83, 25, R),
            CuratedPlayer("Carlos Balebas", Position.CDM, 80, 87, 22, R),
            CuratedPlayer("Youri Tieleman", Position.CM, 82, 82, 29, R),
            CuratedPlayer("Andrey Santana", Position.CM, 77, 86, 22, R),
            CuratedPlayer("Mason Mounter", Position.CAM, 79, 80, 27, R),
            CuratedPlayer("Marcus Rushford", Position.LW, 82, 83, 28, R),
            CuratedPlayer("Matheus Cunhal", Position.ST, 83, 85, 27, R),
            CuratedPlayer("Bryan Mbeuma", Position.RW, 83, 85, 27, L),
            CuratedPlayer("Benjamin Šestić", Position.ST, 81, 88, 23, R),
            CuratedPlayer("Joshua Zirko", Position.ST, 78, 83, 25, R),
            CuratedPlayer("Amad Dialo", Position.RW, 80, 85, 24, L)
        ),

        "Northbank FC" to listOf(
            CuratedPlayer("David Rayas", Position.GK, 85, 86, 31, R),
            CuratedPlayer("Kepa Arrieta", Position.GK, 79, 80, 32, R),
            CuratedPlayer("Illan Mesnard", Position.GK, 77, 82, 26, R),
            CuratedPlayer("William Salibas", Position.CB, 86, 89, 25, R),
            CuratedPlayer("Gabriel Magalhos", Position.CB, 85, 86, 28, L),
            CuratedPlayer("Ben Whitmore", Position.RB, 83, 83, 28, R),
            CuratedPlayer("Jurriën Timmer", Position.RB, 83, 86, 25, R),
            CuratedPlayer("Riccardo Calafiore", Position.CB, 81, 86, 24, L),
            CuratedPlayer("Cristhian Mosquero", Position.CB, 79, 87, 21, R),
            CuratedPlayer("Piero Hincal", Position.CB, 82, 86, 24, L),
            CuratedPlayer("Ezri Konsal", Position.CB, 80, 82, 28, R),
            CuratedPlayer("Myles Lewin", Position.LB, 78, 87, 19, L),
            CuratedPlayer("Martin Ødemark", Position.CAM, 87, 88, 27, L),
            CuratedPlayer("Declan Ryce", Position.CM, 86, 87, 27, R),
            CuratedPlayer("Bruno Guimaron", Position.CDM, 85, 86, 28, R),
            CuratedPlayer("Martín Zubieta", Position.CDM, 85, 87, 27, R),
            CuratedPlayer("Eberechi Ezenwa", Position.CAM, 82, 84, 28, R),
            CuratedPlayer("Mikel Merinos", Position.CM, 82, 82, 30, L),
            CuratedPlayer("Kai Haverz", Position.ST, 83, 84, 27, L),
            CuratedPlayer("Bukayo Sakala", Position.RW, 87, 89, 24, L),
            CuratedPlayer("Viktor Györy", Position.ST, 85, 87, 28, R),
            CuratedPlayer("Noni Madueka", Position.RW, 80, 84, 24, L),
            CuratedPlayer("Christos Tzolas", Position.LW, 78, 82, 24, R)
        ),

        "North Tottenham" to listOf(
            CuratedPlayer("Guglielmo Vicari", Position.GK, 83, 85, 29, R),
            CuratedPlayer("Fraser Forrington", Position.GK, 74, 74, 38, R),
            CuratedPlayer("Cristian Romeiro", Position.CB, 85, 86, 28, R),
            CuratedPlayer("Micky van der Venn", Position.CB, 83, 87, 25, L),
            CuratedPlayer("Pedro Porres", Position.RB, 82, 84, 26, R),
            CuratedPlayer("Destiny Udogi", Position.LB, 81, 85, 23, L),
            CuratedPlayer("Radu Drăgulescu", Position.CB, 79, 84, 24, R),
            CuratedPlayer("Djed Spencer", Position.RB, 78, 83, 25, R),
            CuratedPlayer("Ben Davison", Position.CB, 76, 76, 33, L),
            CuratedPlayer("Jamie Maddox", Position.CAM, 83, 83, 29, R),
            CuratedPlayer("Yves Bissou", Position.CDM, 80, 81, 29, R),
            CuratedPlayer("Pape Sarré", Position.CM, 80, 86, 23, R),
            CuratedPlayer("Rodrigo Bentancourt", Position.CM, 81, 82, 29, R),
            CuratedPlayer("Dejan Kulovic", Position.CAM, 83, 85, 26, L),
            CuratedPlayer("Lucas Bergvold", Position.CM, 77, 87, 20, R),
            CuratedPlayer("Archie Greyson", Position.CM, 76, 85, 20, R),
            CuratedPlayer("Son Hyeon-woo", Position.LW, 85, 85, 34, R),
            CuratedPlayer("Dominic Solanka", Position.ST, 82, 83, 28, R),
            CuratedPlayer("Brennan Johnstone", Position.RW, 81, 84, 25, L),
            CuratedPlayer("Richalton", Position.ST, 80, 80, 29, R),
            CuratedPlayer("Timo Wernher", Position.LW, 78, 78, 30, R),
            CuratedPlayer("Wilson Odober", Position.LW, 76, 84, 21, R),
            CuratedPlayer("Mikey Moores", Position.LW, 72, 85, 18, R)
        ),

        "Westbridge FC" to listOf(
            CuratedPlayer("Emiliano Martínes", Position.GK, 85, 85, 34, R),
            CuratedPlayer("Robert Sancho", Position.GK, 80, 82, 28, R),
            CuratedPlayer("Gaga Slonik", Position.GK, 74, 84, 22, R),
            CuratedPlayer("Mike Pendrick", Position.GK, 70, 83, 21, R),
            CuratedPlayer("Levi Colwyn", Position.CB, 83, 88, 23, L),
            CuratedPlayer("Wesley Fofan", Position.CB, 82, 85, 25, R),
            CuratedPlayer("Maxence Lacroise", Position.CB, 81, 84, 26, R),
            CuratedPlayer("Valentín Barcos", Position.LB, 77, 84, 22, L),
            CuratedPlayer("Marco Palestrini", Position.RB, 73, 83, 21, R),
            CuratedPlayer("Jorrel Haton", Position.CB, 79, 87, 20, L),
            CuratedPlayer("Reece Jameson", Position.RB, 83, 84, 26, R),
            CuratedPlayer("Malo Gustave", Position.RB, 80, 85, 23, R),
            CuratedPlayer("Pep Chavarrín", Position.LB, 76, 83, 24, L),
            CuratedPlayer("Aaron Anselmi", Position.CB, 72, 84, 21, R),
            CuratedPlayer("Cody Palmer", Position.CAM, 87, 90, 24, L),
            CuratedPlayer("Moisés Caizares", Position.CDM, 86, 88, 25, R),
            CuratedPlayer("Roméo Lavi", Position.CDM, 78, 85, 22, R),
            CuratedPlayer("Jordan Henders", Position.CM, 76, 76, 36, R),
            CuratedPlayer("Jamie Gittons", Position.LW, 80, 87, 22, R),
            CuratedPlayer("Pedro Netto", Position.RW, 82, 84, 26, R),
            CuratedPlayer("João Pedrosa", Position.ST, 82, 85, 25, R),
            CuratedPlayer("Morgan Rodgers", Position.CAM, 82, 87, 24, R),
            CuratedPlayer("Danny Welby", Position.ST, 78, 78, 35, R),
            CuratedPlayer("Emanuel Emegh", Position.ST, 78, 86, 23, R),
            CuratedPlayer("Geovany Quental", Position.RW, 77, 88, 19, L),
            CuratedPlayer("Estêvão Willas", Position.RW, 80, 90, 19, L)
        ),

        "Merseyside FC" to listOf(
            CuratedPlayer("Alisson Barreto", Position.GK, 87, 87, 34, R),
            CuratedPlayer("Giorgi Mamardas", Position.GK, 83, 88, 26, R),
            CuratedPlayer("Freddie Woodrow", Position.GK, 72, 73, 30, R),
            CuratedPlayer("Vítězslav Jares", Position.GK, 73, 80, 25, R),
            CuratedPlayer("Harvey Davin", Position.GK, 66, 78, 23, R),
            CuratedPlayer("Virgil van Doorn", Position.CB, 88, 88, 35, R),
            CuratedPlayer("Joe Gomera", Position.CB, 80, 80, 29, R),
            CuratedPlayer("Jérémy Jacquin", Position.CB, 75, 85, 21, R),
            CuratedPlayer("Milos Kerkes", Position.LB, 80, 86, 23, L),
            CuratedPlayer("Conor Bradfield", Position.RB, 78, 85, 23, R),
            CuratedPlayer("Alexis MacAlinden", Position.CM, 85, 86, 28, R),
            CuratedPlayer("Dominik Szobos", Position.CM, 84, 86, 26, R),
            CuratedPlayer("Florian Wirtzer", Position.CAM, 88, 91, 23, R),
            CuratedPlayer("Wataru Endoh", Position.CDM, 78, 78, 33, R),
            CuratedPlayer("Ryan Gravenberg", Position.CDM, 84, 88, 24, R),
            CuratedPlayer("Treymaurice Nyoni", Position.CM, 70, 83, 19, R),
            CuratedPlayer("Cody Gakpa", Position.LW, 84, 85, 27, R),
            CuratedPlayer("Alexander Isaksen", Position.ST, 87, 89, 27, R),
            CuratedPlayer("Federico Chiesi", Position.RW, 82, 82, 29, R),
            CuratedPlayer("Hugo Ekiti", Position.ST, 82, 88, 24, R),
            CuratedPlayer("Bradley Barcala", Position.LW, 83, 88, 24, R),
            CuratedPlayer("Victor Muñoz", Position.RW, 72, 83, 21, R),
            CuratedPlayer("Lewis Koumakis", Position.LW, 70, 82, 21, R)
        ),

        // ---------------------------------------------------------- Germany
        "Bavaria Munchen" to listOf(
            CuratedPlayer("Manuel Neumaier", Position.GK, 87, 87, 40, R),
            CuratedPlayer("Sven Ulricher", Position.GK, 74, 74, 38, R),
            CuratedPlayer("Jonas Urbach", Position.GK, 75, 85, 23, R),
            CuratedPlayer("Dayot Upameno", Position.CB, 85, 87, 28, R),
            CuratedPlayer("Kim Min-je", Position.CB, 83, 84, 30, R),
            CuratedPlayer("Jonathan Taho", Position.CB, 84, 85, 30, R),
            CuratedPlayer("Alphonso Davison", Position.LB, 85, 88, 26, L),
            CuratedPlayer("Hiroki Itoh", Position.CB, 79, 83, 27, L),
            CuratedPlayer("Sacha Boeyer", Position.RB, 79, 83, 26, R),
            CuratedPlayer("Josip Stanić", Position.RB, 79, 82, 26, R),
            CuratedPlayer("Nathaniel Browne", Position.LB, 76, 84, 23, L),
            CuratedPlayer("Deniz Oflek", Position.CB, 68, 80, 19, R),
            CuratedPlayer("Jamal Musial", Position.CAM, 87, 92, 23, R),
            CuratedPlayer("Joshua Kimmler", Position.CDM, 86, 86, 31, R),
            CuratedPlayer("Aleksandar Pavlov", Position.CDM, 80, 87, 22, R),
            CuratedPlayer("Konrad Leimer", Position.CM, 82, 82, 29, R),
            CuratedPlayer("Tom Bischoffe", Position.CM, 76, 86, 21, R),
            CuratedPlayer("Ismael Saibo", Position.CAM, 78, 84, 25, R),
            CuratedPlayer("Arijon Ibrahimi", Position.CAM, 73, 85, 20, R),
            CuratedPlayer("Bryan Zaragosa", Position.LW, 78, 82, 25, R),
            CuratedPlayer("Harry Kearns", Position.ST, 90, 90, 33, R),
            CuratedPlayer("Michael Olisa", Position.RW, 86, 89, 25, L),
            CuratedPlayer("Luis Dias", Position.LW, 85, 86, 30, R),
            CuratedPlayer("Serge Gnabri", Position.RW, 82, 82, 31, R)
        ),

        "Dortmund Westfalen" to listOf(
            CuratedPlayer("Gregor Kobelt", Position.GK, 85, 87, 29, R),
            CuratedPlayer("Alexander Meyers", Position.GK, 72, 72, 35, R),
            CuratedPlayer("Nico Schlotter", Position.CB, 84, 87, 27, L),
            CuratedPlayer("Waldemar Antonov", Position.CB, 81, 82, 30, R),
            CuratedPlayer("Ramy Bensebain", Position.LB, 80, 81, 31, L),
            CuratedPlayer("Julian Ryers", Position.RB, 79, 81, 29, R),
            CuratedPlayer("Niklas Süllow", Position.CB, 81, 81, 31, R),
            CuratedPlayer("Yan Coutin", Position.RB, 79, 84, 24, R),
            CuratedPlayer("Julian Brandner", Position.CAM, 83, 83, 30, L),
            CuratedPlayer("Marcel Sabitz", Position.CM, 82, 82, 32, R),
            CuratedPlayer("Pascal Grosser", Position.CM, 79, 79, 35, R),
            CuratedPlayer("Emre Caner", Position.CDM, 80, 80, 32, R),
            CuratedPlayer("Felix Nmecher", Position.CM, 79, 85, 26, R),
            CuratedPlayer("Giovanni Reynal", Position.CAM, 78, 83, 24, R),
            CuratedPlayer("Serhou Guirass", Position.ST, 84, 85, 30, R),
            CuratedPlayer("Donyell Malens", Position.RW, 81, 82, 27, R),
            CuratedPlayer("Maximilian Beierle", Position.ST, 80, 86, 24, R),
            CuratedPlayer("Julien Duranv", Position.LW, 75, 86, 20, R),
            CuratedPlayer("Cole Campbells", Position.LW, 70, 82, 20, R)
        ),

        // ----------------------------------------------------------- France
        "Paris Royale" to listOf(
            CuratedPlayer("Matvey Safronov", Position.GK, 80, 84, 27, R),
            CuratedPlayer("Arnau Tenes", Position.GK, 77, 84, 25, R),
            CuratedPlayer("Marquinho Costas", Position.CB, 85, 85, 32, R),
            CuratedPlayer("Achraf Hakim", Position.RB, 86, 86, 28, R),
            CuratedPlayer("Nuno Mendès", Position.LB, 85, 88, 24, L),
            CuratedPlayer("Willian Pachon", Position.CB, 83, 87, 25, L),
            CuratedPlayer("Lucas Beraldino", Position.CB, 78, 85, 23, L),
            CuratedPlayer("Milan Škrinar", Position.CB, 81, 81, 31, R),
            CuratedPlayer("Presnel Kimbembe", Position.CB, 79, 79, 31, L),
            CuratedPlayer("Vitinho Ferreira", Position.CM, 86, 88, 26, R),
            CuratedPlayer("João Nevez", Position.CDM, 84, 89, 22, R),
            CuratedPlayer("Warren Zaïreau", Position.CM, 83, 89, 20, R),
            CuratedPlayer("Fabián Rueda", Position.CM, 84, 84, 30, L),
            CuratedPlayer("Kang-min Lee", Position.CAM, 81, 83, 25, L),
            CuratedPlayer("Senny Mayol", Position.CM, 75, 86, 20, R),
            CuratedPlayer("Ousmane Dembel", Position.RW, 87, 87, 29, B),
            CuratedPlayer("Gonçalo Ramires", Position.ST, 83, 86, 25, R),
            CuratedPlayer("Randal Kolo Muan", Position.ST, 81, 83, 27, R),
            CuratedPlayer("Marco Asensi", Position.CAM, 80, 80, 30, L),
            CuratedPlayer("Désiré Douay", Position.RW, 82, 90, 21, R)
        ),

        // ------------------------------------------------------------ Italy
        "Milano Rossoneri" to listOf(
            CuratedPlayer("Mike Magnan", Position.GK, 86, 86, 31, R),
            CuratedPlayer("Marco Sportiel", Position.GK, 74, 74, 34, R),
            CuratedPlayer("Theo Hernandes", Position.LB, 85, 85, 29, L),
            CuratedPlayer("Fikayo Tomorin", Position.CB, 82, 83, 28, R),
            CuratedPlayer("Strahinja Pavlov", Position.CB, 80, 84, 25, L),
            CuratedPlayer("Emerson Royale", Position.RB, 78, 79, 27, R),
            CuratedPlayer("Davide Calabres", Position.RB, 79, 79, 29, R),
            CuratedPlayer("Matteo Gabbiano", Position.CB, 78, 80, 26, R),
            CuratedPlayer("Malick Thiawo", Position.CB, 79, 85, 25, R),
            CuratedPlayer("Tijjani Reinders", Position.CM, 84, 86, 28, R),
            CuratedPlayer("Youssouf Fofan", Position.CDM, 81, 82, 27, R),
            CuratedPlayer("Ruben Loftus", Position.CM, 80, 80, 30, R),
            CuratedPlayer("Ismaël Bennace", Position.CDM, 79, 80, 28, L),
            CuratedPlayer("Yunus Musa", Position.CM, 78, 83, 23, R),
            CuratedPlayer("Rafael Leal", Position.LW, 86, 88, 27, R),
            CuratedPlayer("Christian Puliš", Position.RW, 83, 84, 27, R),
            CuratedPlayer("Álvaro Morato", Position.ST, 80, 80, 33, R),
            CuratedPlayer("Samuel Chukwuez", Position.RW, 79, 82, 27, L),
            CuratedPlayer("Noah Okafo", Position.ST, 77, 81, 26, R),
            CuratedPlayer("Tammy Abram", Position.ST, 79, 80, 28, R)
        ),

        "Milano Nerazzurri" to listOf(
            CuratedPlayer("Yann Sommers", Position.GK, 84, 84, 37, R),
            CuratedPlayer("Josep Martínes", Position.GK, 76, 82, 28, R),
            CuratedPlayer("Alessandro Baston", Position.CB, 86, 87, 27, L),
            CuratedPlayer("Benjamin Pavan", Position.CB, 83, 83, 30, R),
            CuratedPlayer("Francesco Acerbo", Position.CB, 80, 80, 38, L),
            CuratedPlayer("Stefan de Vrijer", Position.CB, 80, 80, 34, R),
            CuratedPlayer("Yann Bissek", Position.CB, 79, 85, 25, R),
            CuratedPlayer("Federico Dimarchi", Position.LB, 84, 85, 28, L),
            CuratedPlayer("Matteo Darmiano", Position.RB, 78, 78, 36, R),
            CuratedPlayer("Carlos Augustino", Position.LB, 79, 82, 27, L),
            CuratedPlayer("Nicolò Barello", Position.CM, 86, 87, 29, R),
            CuratedPlayer("Hakan Çalhan", Position.CDM, 85, 85, 32, R),
            CuratedPlayer("Henrikh Mkhitary", Position.CM, 81, 81, 37, R),
            CuratedPlayer("Davide Frattini", Position.CM, 80, 83, 26, R),
            CuratedPlayer("Piotr Zielińsk", Position.CM, 81, 81, 32, L),
            CuratedPlayer("Kristjan Asllan", Position.CDM, 77, 83, 24, R),
            CuratedPlayer("Lautaro Martínes", Position.ST, 87, 88, 28, R),
            CuratedPlayer("Marcus Thurau", Position.ST, 84, 86, 28, R),
            CuratedPlayer("Mehdi Taremy", Position.ST, 80, 80, 33, R),
            CuratedPlayer("Marko Arnautov", Position.ST, 76, 76, 37, R)
        ),

        "Torino Bianconeri" to listOf(
            CuratedPlayer("Michele Di Gregori", Position.GK, 82, 85, 29, R),
            CuratedPlayer("Mattia Perini", Position.GK, 78, 78, 33, R),
            CuratedPlayer("Bremo Silva", Position.CB, 85, 86, 29, R),
            CuratedPlayer("Pierre Kalou", Position.CB, 80, 83, 26, R),
            CuratedPlayer("Andrea Cambias", Position.LB, 82, 86, 26, L),
            CuratedPlayer("Federico Gattin", Position.CB, 80, 83, 28, R),
            CuratedPlayer("Juan Cabale", Position.LB, 78, 84, 25, L),
            CuratedPlayer("Danilo Lopes", Position.CB, 79, 79, 34, R),
            CuratedPlayer("Teun Koopmans", Position.CM, 83, 84, 28, R),
            CuratedPlayer("Douglas Luís", Position.CDM, 82, 83, 28, R),
            CuratedPlayer("Khéphren Thurau", Position.CM, 81, 87, 25, R),
            CuratedPlayer("Manuel Locatell", Position.CDM, 81, 82, 28, R),
            CuratedPlayer("Weston McKennal", Position.CM, 79, 80, 28, R),
            CuratedPlayer("Nicolò Fagiolo", Position.CM, 77, 83, 25, R),
            CuratedPlayer("Dušan Vlahov", Position.ST, 84, 86, 26, R),
            CuratedPlayer("Kenan Yıldır", Position.CAM, 82, 90, 21, L),
            CuratedPlayer("Francisco Conceiç", Position.RW, 80, 86, 23, L),
            CuratedPlayer("Nicolás Gonzáles", Position.RW, 80, 81, 28, R),
            CuratedPlayer("Timothy Weal", Position.RW, 76, 80, 26, R),
            CuratedPlayer("Arkadiusz Milika", Position.ST, 78, 78, 32, R)
        ),

        "Roma Capitolina" to listOf(
            CuratedPlayer("Mile Svilac", Position.GK, 82, 86, 27, R),
            CuratedPlayer("Mathew Ryans", Position.GK, 76, 76, 34, R),
            CuratedPlayer("Gianluca Mancino", Position.CB, 81, 82, 30, R),
            CuratedPlayer("Evan Ndicko", Position.CB, 82, 85, 26, L),
            CuratedPlayer("Mario Hermosín", Position.CB, 80, 80, 31, L),
            CuratedPlayer("Mats Hummel", Position.CB, 79, 79, 37, R),
            CuratedPlayer("Zeki Çeliker", Position.RB, 78, 79, 29, R),
            CuratedPlayer("Angel Tasende", Position.LB, 80, 82, 29, L),
            CuratedPlayer("Saud Abdulla", Position.RB, 76, 82, 27, R),
            CuratedPlayer("Lorenzo Pellegrin", Position.CAM, 82, 82, 30, R),
            CuratedPlayer("Bryan Cristant", Position.CDM, 80, 80, 31, R),
            CuratedPlayer("Manu Konan", Position.CM, 80, 86, 25, R),
            CuratedPlayer("Enzo Le Fey", Position.CM, 78, 83, 26, R),
            CuratedPlayer("Leandro Paredo", Position.CDM, 79, 79, 32, R),
            CuratedPlayer("Tommaso Baldanz", Position.CAM, 76, 84, 23, R),
            CuratedPlayer("Paulo Dybal", Position.CAM, 83, 83, 32, L),
            CuratedPlayer("Artem Dovbyko", Position.ST, 82, 84, 29, R),
            CuratedPlayer("Matías Soulet", Position.RW, 79, 86, 23, L),
            CuratedPlayer("Stephan El Shaar", Position.LW, 79, 79, 33, R),
            CuratedPlayer("Alexis Saelemaeker", Position.RW, 78, 81, 27, R),
            CuratedPlayer("Eldor Shomurod", Position.ST, 75, 77, 31, R)
        )
    )

    /** True when this club has a hand-authored squad. */
    fun hasCurated(name: String): Boolean = byClubName.containsKey(name)

    /** Total number of hand-authored players, for diagnostics and tests. */
    val curatedPlayerCount: Int get() = byClubName.values.sumOf { it.size }
}
