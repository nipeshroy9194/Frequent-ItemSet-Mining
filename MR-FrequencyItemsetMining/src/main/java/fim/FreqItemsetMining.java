package fim;

/**
* @author: Burhan Sadliwala
* @since: 2nd Nov, 2019
*/

import java.util.StringTokenizer;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class FreqItemsetMining extends Configured implements Tool {
	private static final Logger logger = LogManager.getLogger(FreqItemsetMining.class);
	private static final int K = 1000; // Initialize K for Synthetic Graph()
	private static final int N = 10; // Initialize number of iterations

	// ***************************************************************************
	// First Map-Reduce Job - Creates Node vertices from input files.
	/*
	 * This class defines a mapper which consumes the input files generated from Spark for the Synthetic Graph.
	 */
	public static class InitialMapper extends Mapper<Object, Text, Text, Text> {
		/**
		 * Emits the graph structure as its generated from Spark.
		 */
		@Override
		public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
			final StringTokenizer itr = new StringTokenizer(value.toString());
			while (itr.hasMoreTokens()) {
				String token = itr.nextToken().replace("(", "").replace(")", "");
				String v1 = token.split(",")[0], v2 = token.split(",")[1];
				context.write(new Text(v1), new Text(v2));
			}
		}
	}

	public static class InitialReducer extends Reducer<Text, Text, Text, Text> {
		/**
		 * Generates Node objects for each vertex in the graph
		 * Node has ID, Adjacency List and PageRank.
		 */
		@Override
		public void reduce(final Text key, final Iterable<Text> values, final Context context) throws IOException, InterruptedException {

		}
	}

	// ***************************************************************************

	@Override
	public int run(final String[] args) throws Exception {
		
		final Configuration conf = getConf();
		Job job1 = Job.getInstance(conf, "Adjacency Lists");
		job1.setJarByClass(FreqItemsetMining.class);

		final Configuration jobConf = job1.getConfiguration();
		jobConf.set("mapreduce.output.textoutputformat.separator", "\t");

		job1.setMapperClass(InitialMapper.class);
		job1.setReducerClass(InitialReducer.class);

		job1.setMapOutputKeyClass(Text.class);
		job1.setMapOutputValueClass(Text.class);

		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(Text.class);

		job1.setInputFormatClass(TextInputFormat.class);
		job1.setOutputFormatClass(TextOutputFormat.class);

		TextInputFormat.addInputPath(job1, new Path(args[0]));
		TextOutputFormat.setOutputPath(job1, new Path(args[1], "adjList"));

		return job1.waitForCompletion(true) ? 1 : 0;

		}


	public static void main(final String[] args) {
		if (args.length != 2) {
			throw new Error("Two arguments required:\n<input-dir> <output-dir>");
		}

		try {
			ToolRunner.run(new FreqItemsetMining(), args);
		} catch (final Exception e) {
			logger.error("", e);
		}
	}
}
