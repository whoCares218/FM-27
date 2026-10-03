package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.SponsorOffer
import kotlin.random.Random

/**
 * Sponsorship offers. Five deals are put to the board before each season, each
 * trading a safe guaranteed income against a riskier performance bonus. The
 * choice is a genuine decision rather than a cosmetic label.
 */
object SponsorshipEngine {

    /** Builds five offers scaled to the club's reputation. */
    fun generateOffers(club: Club, random: Random): List<SponsorOffer> {
        val scale = when {
            club.reputation >= 88 -> 1.0
            club.reputation >= 78 -> 0.62
            club.reputation >= 68 -> 0.34
            club.reputation >= 58 -> 0.18
            else -> 0.09
        }
        val base = 120_000_000.0 * scale

        fun jitter(value: Double): Long =
            (value * (0.92 + random.nextDouble() * 0.16)).toLong()

        return listOf(
            SponsorOffer(
                id = 1,
                name = "Global Sportswear",
                tier = "Global",
                upfront = jitter(base * 0.22),
                seasonal = jitter(base * 0.78),
                bonusCondition = "Win the league title",
                bonusAmount = jitter(base * 0.55),
                risk = "Huge guaranteed money, but the bonus is all or nothing on the title."
            ),
            SponsorOffer(
                id = 2,
                name = "Continental Telecom",
                tier = "National",
                upfront = jitter(base * 0.34),
                seasonal = jitter(base * 0.60),
                bonusCondition = "Finish in the top four",
                bonusAmount = jitter(base * 0.30),
                risk = "Balanced deal: a solid retainer with a realistic European bonus."
            ),
            SponsorOffer(
                id = 3,
                name = "Local Brewery",
                tier = "Local",
                upfront = jitter(base * 0.55),
                seasonal = jitter(base * 0.42),
                bonusCondition = "Finish in the top half",
                bonusAmount = jitter(base * 0.14),
                risk = "Most money up front, lowest ceiling if the season goes well."
            ),
            SponsorOffer(
                id = 4,
                name = "Streaming Platform",
                tier = "Global",
                upfront = jitter(base * 0.30),
                seasonal = jitter(base * 0.36),
                bonusCondition = "Win a cup",
                bonusAmount = jitter(base * 0.48),
                risk = "Modest retainer with a big cup bonus. Cup runs are unpredictable."
            ),
            SponsorOffer(
                id = 5,
                name = "Energy Drinks Co.",
                tier = "National",
                upfront = jitter(base * 0.40),
                seasonal = jitter(base * 0.30),
                bonusCondition = "Avoid relegation",
                bonusAmount = jitter(base * 0.20),
                risk = "Safe, low-risk income with a bonus almost any decent season secures."
            )
        )
    }

    /** Converts a chosen offer into a signed sponsorship. */
    fun sign(offer: SponsorOffer): com.footymanager.simulator.domain.model.Sponsorship =
        com.footymanager.simulator.domain.model.Sponsorship(
            name = offer.name,
            tier = offer.tier,
            upfront = offer.upfront,
            seasonal = offer.seasonal,
            bonusCondition = offer.bonusCondition,
            bonusAmount = offer.bonusAmount
        )

    /** True when the season's finish satisfies the sponsorship bonus condition. */
    fun bonusEarned(condition: String, finalPosition: Int): Boolean = when {
        condition.contains("title", ignoreCase = true) -> finalPosition == 1
        condition.contains("top four", ignoreCase = true) -> finalPosition <= 4
        condition.contains("top half", ignoreCase = true) -> finalPosition <= 10
        else -> false
    }
}
