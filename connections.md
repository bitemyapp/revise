Revise's connection management is a little different from how most database clients work.

The send operations are messages passed to an agent, with a promise (like an MVar) kicked back to the sender.

The promise gets associated with the unique response token and the async agent that serializes access to the socket sends the protobuf after putting the promise in a mapping of token -> promise.

The "reader" is a future running a loop that keeps sending messages back to the agent to deliver promises and disassociate them from the "on deck" mapping, it also has a "signalling" promise to short-circuit itself.

The result of running all queries is a promise and can be deref'd (the @ operator) in order to block on the result.

This leads to more efficient use of the connections (you don't tie up the whole connection while waiting on a result) and makes the API async by default.

As long as RethinkDB sends the results in the order they completed rather than strictly ordering by when they were received, this leads to connections not getting locked for the duration of longer queries. Agent-based serialization of operations means that there is no locking in my code.

Written it in 2 hours last night after a few days of thinking about it.

Some of the design could potentially commend the use of core.async, but the narrow scope of message-passing in this design didn't seem to merit it. It's not a complicated dataflow, just serialized operations against a shared resource. The semantics of how agent state works were a better fit too.
