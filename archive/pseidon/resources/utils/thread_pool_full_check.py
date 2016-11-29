#!/usr/bin/env python
import urllib2
import json
import itertools
import sys

if len(sys.argv) == 2:

    server = sys.argv[1]    
    req = urllib2.Request("http://" + str(server) + ":8282/metrics")
    f = opener.open(req)
    data = f.read()
    j = json.loads(data)

    d = j[0]['pseidon.core.queue.dynamic-threads']['value'].values()
    merged = list(itertools.chain.from_iterable(d))
    
    if not filter(lambda x: '100' in x, merged):
       print str(0)
    else:
       print str(-1)

else:
  raise Exception("Please specify a server name as argument")




