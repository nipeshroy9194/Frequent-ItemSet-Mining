# Map Reduce Cluster to Preprocess input Dataset

Code author
-----------
- Burhan Sadliwala
- Nipesh Roy
- Xiang Wei

Installation
------------
These components are installed:
- JDK 1.8
- Scala 2.11.12
- Hadoop 2.8.5
- Spark 2.3.1
- Maven
- AWS CLI (for EMR execution)


Execution
---------
All of the build & execution commands are organized in the Makefile.
1) Open command prompt.
2) Navigate to directory where project files unzipped.
3) Edit the Makefile to customize the environment at the top.<br/>
	Sufficient for standalone: hadoop.root, jar.name, local.input<br/>
	Other defaults acceptable for running standalone.<br/>
4) Standalone Hadoop:<br/>
	make switch-standalone		-- set standalone Hadoop environment (execute once)<br/>
	make local<br/>
5) Pseudo-Distributed Hadoop: (https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SingleCluster.html#Pseudo-Distributed_Operation)<br/>
	make switch-pseudo			-- set pseudo-clustered Hadoop environment (execute once)<br/>
	make pseudo					-- first execution<br/>
	make pseudoq				-- later executions since namenode and datanode already running<br/>
6) AWS EMR Hadoop: (you must configure the emr.* config parameters at top of Makefile)<br/>
	make upload-input-aws		-- only before first execution<br/>
	make aws					-- check for successful execution with web interface (aws.amazon.com)<br/>
	make download-output-aws			-- after successful execution & termination<br/>


References
----------
This project structure and makefile is referred from the example code provided in HW1.
