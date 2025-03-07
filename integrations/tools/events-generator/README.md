### Events generator tool

This python tool provides functionality to generate and index sample events for Fortishield's indices.

#### Getting started

Create a virtual environment to install the dependencies of the project.

```console
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Start the events' generator with `./run.py` or `python run.py`. The program takes no required
arguments, as it's configured with default values that will work in most cases during development.
To know more about its capabilities and arguments, display the help menu with `-h`.

As for now, this tool generates events for the `fortishield-alerts-4.x-*` and `fortishield-archives-4.x-*` indices.
Since 4.8.0, these indices are aliased to `fortishield-alerts` and `fortishield-archives`. If you need to, run the
[indexer-ism-init.sh](../../../distribution/src/bin/indexer-ism-init.sh) script to create them. This is important as, by default, the tool will write to
the `fortishield-alerts` alias. You may also need to create an **index pattern** in _dashboards_ in order to perform
queries to the index from the UI. To do that, go to Dashboards Management > Index Patterns > Create index pattern > fortishield-alerts-4.x-* > timestamp as Time field

Newer indices, like `fortishield-states-vulnerabilities`, are ECS compliant and use a dedicated events' generator.
You can find it in the [ecs](../../../ecs/) folder.


```console
python run.py -o indexer -c 5 -t 1
INFO:event_generator:Inventory created
INFO:event_generator:Publisher created
INFO:event_generator:Event created
{'_index': 'fortishield-alerts-4.x-2024.02.13-000001', '_id': 'dRWno40BZRXLJU5t0u6Z', '_version': 1, 'result': 'created', '_shards': {'total': 2, 'successful': 2, 'failed': 0}, '_seq_no': 168, '_primary_term': 1}
INFO:event_generator:Event created
{'_index': 'fortishield-alerts-4.x-2024.02.13-000001', '_id': 'dhWno40BZRXLJU5t1u6Y', '_version': 1, 'result': 'created', '_shards': {'total': 2, 'successful': 2, 'failed': 0}, '_seq_no': 169, '_primary_term': 1}
INFO:event_generator:Event created
{'_index': 'fortishield-alerts-4.x-2024.02.13-000001', '_id': 'dxWno40BZRXLJU5t2u6i', '_version': 1, 'result': 'created', '_shards': {'total': 2, 'successful': 2, 'failed': 0}, '_seq_no': 170, '_primary_term': 1}
INFO:event_generator:Event created
{'_index': 'fortishield-alerts-4.x-2024.02.13-000001', '_id': 'eBWno40BZRXLJU5t3u6v', '_version': 1, 'result': 'created', '_shards': {'total': 2, 'successful': 2, 'failed': 0}, '_seq_no': 171, '_primary_term': 1}
INFO:event_generator:Event created
{'_index': 'fortishield-alerts-4.x-2024.02.13-000001', '_id': 'eRWno40BZRXLJU5t4u66', '_version': 1, 'result': 'created', '_shards': {'total': 2, 'successful': 2, 'failed': 0}, '_seq_no': 172, '_primary_term': 1}
```
