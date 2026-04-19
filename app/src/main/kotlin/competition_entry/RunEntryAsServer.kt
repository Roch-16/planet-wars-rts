package competition_entry

import games.planetwars.agents.choco.ChocoAgent
import json_rmi.GameAgentServer

fun main() {
    val server = GameAgentServer(port = 8080, agentClass = ChocoAgent::class)
    server.start(wait = true)
}
