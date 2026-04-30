package games.planetwars.agents.choco.Optimization

import games.planetwars.agents.choco.ChocoAgent
import games.planetwars.agents.choco.MCTSParams
import games.planetwars.agents.random.BetterRandomAgent
import competition_entry.GreedyHeuristicAgent
import games.planetwars.agents.evo.SimpleEvoAgent
import games.planetwars.core.GameParamGenerator
import games.planetwars.core.GameParams
import games.planetwars.core.Player
import games.planetwars.runners.GameRunner
import java.util.Locale

private const val DEFAULT_GAMES = 50

private data class EvalParams(
    val attackFraction: Double,
    val topTargets: Int,
    val minDefense: Double,
    val territoryWeight: Double,
    val shipDiffDivisor: Double,
    val earlyGrowth: Double,
    val lateGrowth: Double,
    val earlyTransit: Double,
    val lateTransit: Double,
    val enemyTargetBonus: Double,
    val neutralTargetBonus: Double,
    val ownTargetBonus: Double,
)

private fun parseArgs(args: Array<String>): EvalParams? {
    val values = when {
        args.size == 1 -> args[0].split(',').map { it.trim() }
        args.size == 12 -> args.toList().map { it.trim() }
        else -> return null
    }

    if (values.size != 12) {
        return null
    }

    val attackFraction = values[0].toDoubleOrNull() ?: return null
    val topTargets = values[1].toIntOrNull() ?: return null
    val minDefense = values[2].toDoubleOrNull() ?: return null
    val territoryWeight = values[3].toDoubleOrNull() ?: return null
    val shipDiffDivisor = values[4].toDoubleOrNull() ?: return null
    val earlyGrowth = values[5].toDoubleOrNull() ?: return null
    val lateGrowth = values[6].toDoubleOrNull() ?: return null
    val earlyTransit = values[7].toDoubleOrNull() ?: return null
    val lateTransit = values[8].toDoubleOrNull() ?: return null
    val enemyTargetBonus = values[9].toDoubleOrNull() ?: return null
    val neutralTargetBonus = values[10].toDoubleOrNull() ?: return null
    val ownTargetBonus = values[11].toDoubleOrNull() ?: return null

    return EvalParams(
        attackFraction = attackFraction,
        topTargets = topTargets,
        minDefense = minDefense,
        territoryWeight = territoryWeight,
        shipDiffDivisor = shipDiffDivisor,
        earlyGrowth = earlyGrowth,
        lateGrowth = lateGrowth,
        earlyTransit = earlyTransit,
        lateTransit = lateTransit,
        enemyTargetBonus = enemyTargetBonus,
        neutralTargetBonus = neutralTargetBonus,
        ownTargetBonus = ownTargetBonus,
    )
}

fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    if (parsed == null) {
        // iRace minimizes by default, so invalid parameter sets get worst cost.
        println("1.000000")
        return
    }

    val tunedParams = MCTSParams(
        attackShipsFraction = parsed.attackFraction,
        topTargetsPerSource = parsed.topTargets,
        minDefenseShips = parsed.minDefense,
        territoryWeight = parsed.territoryWeight,
        shipDiffDivisor = parsed.shipDiffDivisor,
        earlyGrowthWeight = parsed.earlyGrowth,
        lateGrowthWeight = parsed.lateGrowth,
        earlyTransitWeight = parsed.earlyTransit,
        lateTransitWeight = parsed.lateTransit,
        enemyTargetBonus = parsed.enemyTargetBonus,
        neutralTargetBonus = parsed.neutralTargetBonus,
        ownTargetBonus = parsed.ownTargetBonus,
    )
    val opponents = listOf(
        //BetterRandomAgent(),
        GreedyHeuristicAgent(),
        SimpleEvoAgent()
    )

    var totalWins = 0
    var totalGames = 0

    for (gameIndex in 0 until DEFAULT_GAMES) {
        val gameParams = GameParamGenerator.randomParams(seed = gameIndex.toLong() + 1L).copy(
            maxTicks = 2000,
            newMapEachRun = true,
        )

        for (opponent in opponents) {
            val runner = GameRunner(
                agent1 = ChocoAgent(tunedParams),
                agent2 = opponent,
                gameParams = gameParams,
            )

            val scores = runner.runGames(1)
            val wins = scores[Player.Player1] ?: 0

            totalWins += wins
            totalGames += 1
        }
    }

    val winRate = totalWins.toDouble() / totalGames
    val cost = 1.0 - winRate
    println(String.format(Locale.US, "%.6f", cost))
}
