// fork:local-addons - loaded into the bundled AIOStreams and AIOMetadata with `node --require`.
// Both servers listen on every network interface by default, which makes Windows Firewall ask the
// user about node.exe and exposes them on the home network. Nuvio talks to them on 127.0.0.1 only,
// so a listen() call that names no host is pinned to the loopback address. Their own code is not
// modified.
'use strict';
const net = require('node:net');
const host = process.env.NUVIO_LISTEN_HOST || '127.0.0.1';
const listen = net.Server.prototype.listen;

net.Server.prototype.listen = function (...args) {
  const first = args[0];
  if (typeof first === 'number' || (typeof first === 'string' && /^\d+$/.test(first))) {
    // listen(port[, host][, backlog][, cb]): add the host when the second argument is not one.
    if (typeof args[1] !== 'string') args.splice(1, 0, host);
  } else if (first && typeof first === 'object' && !Array.isArray(first) && first.path == null && first.host == null) {
    // listen({ port }) with no host.
    args[0] = { ...first, host };
  } else if (args.length === 0 || typeof first === 'function') {
    // listen() / listen(cb): an ephemeral port, still on loopback.
    args.unshift(0, host);
  }
  return listen.apply(this, args);
};
