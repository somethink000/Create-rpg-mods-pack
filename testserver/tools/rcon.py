# Minimal RCON client: python3 rcon.py "command" ["command" ...]; password from server.properties.
import socket, struct, sys, os
props = dict(l.strip().split('=', 1) for l in open(os.path.join(os.path.dirname(__file__), '..', 'server.properties')) if '=' in l and not l.startswith('#'))
s = socket.create_connection(('127.0.0.1', int(props.get('rcon.port', 25575))), timeout=120)
def packet(req_id, kind, body):
    data = struct.pack('<ii', req_id, kind) + body.encode() + b'\x00\x00'
    s.sendall(struct.pack('<i', len(data)) + data)
    size = struct.unpack('<i', s.recv(4))[0]
    buf = b''
    while len(buf) < size: buf += s.recv(size - len(buf))
    return buf[8:-2].decode('utf-8', 'replace')
packet(1, 3, props['rcon.password'])
for cmd in sys.argv[1:]:
    print(f'> {cmd}\n{packet(2, 2, cmd)}')
