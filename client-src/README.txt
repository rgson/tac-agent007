This is the beta 9 version of the SICS TAC Classic AgentWare for Java.

You will need Java 2 SDK 1.4.1 or newer (you can find it at
http://java.sun.com) to be able to develop and run TAC agents
using this AgentWare.


Features of the AgentWare
-------------------------

- automatic connection, login and retrieval of game data
- automatic refreshing of bids and quote information
  (will send bidInfo and getQuote to the server and call the agent when
   the answers been received)
- asynchronous communication with the TAC server
- bookkeeping of transactions so that the agent knows what it own
- window showing the internal state of the agent, bids, ownership, etc.
- logging to disk


Getting the DummyAgent to run
-----------------------------

There are brief documentation about a few of the important methods
and callbacks in the header of the DummyAgent file.

Compiling
---------
Type "compile.bat" (or "compile.sh" for unix) to compile the AgentWare
(and the DummyAgent).

Running
-------
Register your agent at http://tac1.sics.se:8080/ and then enter your
agent name and password in the configuration file 'agent.conf'.

Then type "java -jar tacagent.jar" to run an example agent.

If everything is all right the DummyAgent will connect to the server
and a window showing the "internal" state of the agent will be shown.

Game results and a game viewer can be found at http://tac1.sics.se:8080/
For information about other TAC Classic servers please see
http://www.sics.se/tac/server/


Configuring the AgentWare
-------------------------

The AgentWare is reading the configuration file 'agent.conf' at
startup. This file allows, among other things, the configuration
of log levels and agent implementation. See the file 'agent.conf'
for more information.

Note: by default most TAC Classic servers automatically create a new
game for the agent after a game has ended. You can specify how many
games the agent automatically will play by setting the 'exitAfterGames'
option in the configuration file 'agent.conf'.


If you have any questions or comments regarding this AgentWare
please contact tac-dev@sics.se

-- The SICS TAC Team
