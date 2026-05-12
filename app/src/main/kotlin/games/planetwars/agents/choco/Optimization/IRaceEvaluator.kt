package games.planetwars.agents.choco.Optimization

import games.planetwars.agents.choco.ChocoAgent
import games.planetwars.agents.choco.MCTSParams
import games.planetwars.agents.random.CarefulRandomAgent
import competition_entry.GreedyHeuristicAgent
import games.planetwars.core.GameParamGenerator
import games.planetwars.core.Player
import games.planetwars.runners.GameRunner
import java.util.Locale

private const val DEFAULT_GAMES = 50

private data class EvalParams(
    val attackFraction: Double,
    val attackExecution: Double,
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
    val biasWeight: Double,
)

private fun parseArgs(args: Array<String>): EvalParams? {
    val values = when {
        args.size == 1 -> args[0].split(',').map { it.trim() }
        args.size == 14 -> args.toList().map { it.trim() }
        else -> return null
    }

    if (values.size != 14) return null

    return EvalParams(
        attackFraction     = values[0].toDoubleOrNull()  ?: return null,
        attackExecution    = values[1].toDoubleOrNull()  ?: return null,
        topTargets         = values[2].toIntOrNull()     ?: return null,
        minDefense         = values[3].toDoubleOrNull()  ?: return null,
        territoryWeight    = values[4].toDoubleOrNull()  ?: return null,
        shipDiffDivisor    = values[5].toDoubleOrNull()  ?: return null,
        earlyGrowth        = values[6].toDoubleOrNull()  ?: return null,
        lateGrowth         = values[7].toDoubleOrNull()  ?: return null,
        earlyTransit       = values[8].toDoubleOrNull()  ?: return null,
        lateTransit        = values[9].toDoubleOrNull()  ?: return null,
        enemyTargetBonus   = values[10].toDoubleOrNull() ?: return null,
        neutralTargetBonus = values[11].toDoubleOrNull() ?: return null,
        ownTargetBonus     = values[12].toDoubleOrNull() ?: return null,
        biasWeight         = values[13].toDoubleOrNull() ?: return null,
    )
}

fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    if (parsed == null) {
        println("1.000000")
        return
    }

    val tunedParams = MCTSParams(
        attackShipsFraction  = parsed.attackFraction,
        attackShipsExecution = parsed.attackExecution,
        topTargetsPerSource  = parsed.topTargets,
        minDefenseShips      = parsed.minDefense,
        territoryWeight      = parsed.territoryWeight,
        shipDiffDivisor      = parsed.shipDiffDivisor,
        earlyGrowthWeight    = parsed.earlyGrowth,
        lateGrowthWeight     = parsed.lateGrowth,
        earlyTransitWeight   = parsed.earlyTransit,
        lateTransitWeight    = parsed.lateTransit,
        enemyTargetBonus     = parsed.enemyTargetBonus,
        neutralTargetBonus   = parsed.neutralTargetBonus,
        ownTargetBonus       = parsed.ownTargetBonus,
        biasWeight           = parsed.biasWeight,
    )

    val opponents = listOf(
        CarefulRandomAgent(),
        GreedyHeuristicAgent(),
    )

    var totalWins = 0
    var totalGames = 0

    for (opponentIdx in opponents.indices) {
        val opponent = opponents[opponentIdx]

        for (gameIndex in 0 until DEFAULT_GAMES) {
            val seed = (gameIndex.toLong() + 1L) * 100L + opponentIdx

            val gameParams = GameParamGenerator.randomParams(seed = seed).copy(
                maxTicks = 2000,
                newMapEachRun = true,
            )

            val (agent1, agent2, trackedPlayer) = if (gameIndex % 2 == 0) {
                Triple(ChocoAgent(tunedParams), opponent, Player.Player1)
            } else {
                Triple(opponent, ChocoAgent(tunedParams), Player.Player2)
            }

            val runner = GameRunner(
                agent1 = agent1,
                agent2 = agent2,
                gameParams = gameParams,
            )

            val scores = runner.runGames(1)
            val gamesPlayed = scores.values.sum()

            if (gamesPlayed > 0) {
                totalWins += scores[trackedPlayer] ?: 0
                totalGames += gamesPlayed
            }
        }
    }

    val winRate = if (totalGames > 0) totalWins.toDouble() / totalGames else 0.0
    val cost = 1.0 - winRate
    println(String.format(Locale.US, "%.6f", cost))
}