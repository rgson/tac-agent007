# Agent007
An agent implementation for the [TAC Classic](http://tac.sics.se/page.php?id=3) game.

Created by Robin Gustafsson, Tamme Dittrich and Peter Eriksson.

## Setup
1. Compile the server using the `compile.sh`/`compile.bat` files in the `server-src` directory.  
	(Alt. download the binary version of the [SICS TAC'04 Classic Java Server](http://tac.sics.se/page.php?id=12)).
2. Start the server by running `tacserver.jar` followed by `infoserver.jar`.
3. Register an account for the client on the server website ([http://localhost:8080/](http://localhost:8080/)).
4. Change the `agent.conf` file to match the registered account.
5. Compile the agent using the `compile.sh`/`compile.bat` files in the `client-src` directory.
6. Start the agent by running `tacagent.jar`.

## Implementation
Implemented using the [TAC Classic AgentWare Beta 9](http://tac.sics.se/page.php?id=12) framework for TAC Classic agents.
