package games.planetwars.agents.choco

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.GameState

class ChocoAgent(val mctsParams: MCTSParams = MCTSParams()) : PlanetWarsPlayer()
{
    private val mcts = MonteCarloTreeSearch(mctsParams)

    override fun getAgentType() : String
    {
        return "ChocoAgent - MCTS"
    }

    override fun getAction(gameState : GameState): Action
    {
        val wrappedState = GameStateWrapper(gameState.deepCopy(), player, params, mctsParams)
        return mcts.run(wrappedState, player)
    }
}
