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
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
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
				String token = itr.nextToken().replace("(","").replace(")", "");
				String v1 = token.split(",")[0], v2 = token.split(",")[1];
				context.write(new Text(v1),new Text(v2));
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
	// MapReduce Job - Loops Jobs for number of iterations provided for calculating Delta.
	/*
	* This class defines a mapper which consumes the "intermediate_output".
	* It generates the final count of distinct triangles.
	*/
	public static class DeltaMapper extends Mapper<Object, Text, Text, Text> {
		/**
		*  Emits contibution of each node for all nodes in its adjacency list
		*
		*/
		@Override
		public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {


		}
	}

	public static class DeltaReducer extends Reducer<Text, Text, Text, Text> {
		private static double offset = Math.pow(10D, 10D);

		/**
		 * Sums up all contributions for all nodes including the dummy node which is then updated to a global counter
		 */
		@Override
		public void reduce(final Text key, final Iterable<Text> values, final Context context) throws IOException, InterruptedException {
		}
	}

	// ***************************************************************************
	// MapReduce Job - Loops Jobs for number of iterations provided for calculating PageRank.
	/*
	 * This class defines a mapper which consumes the "iter_delta" for current iteration.
	 * It adds the delta contribution to each page.
	 */
	public static class PRMapper extends Mapper<Object, Text, Text, Text> {
		private static double delta;

		@Override
		public void setup(Context context) {

		}

		/**
		 * The mapper only distributes the delta to current contributions for each page.
		 * Resets dummy to 0
		 */
		@Override
		public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {

		}
	}

	public static class PRReducer extends Reducer<Text, Text, Text, Text> {
		private static double offset = Math.pow(10D, 10D);

		/**
		 * Applies the pageRank formula on the final contributions after delta is added
		 * to calculate new PageRank
		 *
		 */
		@Override
		public void reduce(final Text key, final Iterable<Text> values, final Context context) throws IOException, InterruptedException {

		}
	}
	// ***************************************************************************

	@Override
	public int run(final String[] args) throws Exception {
		// JOB 1
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

		job1.waitForCompletion(true);

		// Loop of N Jobs
		Job jobDelta, jobPR;

		double delta = 0D;
		double offSet = Math.pow(10D, 10D);

		for(int i = 0; i < N; i++) {
			//MR Job for Delta calculation
			jobDelta = Job.getInstance(conf, ("Delta_Job" + (i+1)));
			jobDelta.setJarByClass(FreqItemsetMining.class);

			final Configuration jobDeltaConf = jobDelta.getConfiguration();
			jobDeltaConf.set("mapreduce.output.textoutputformat.separator", "\t");

			jobDelta.setMapperClass(DeltaMapper.class);
			jobDelta.setReducerClass(DeltaReducer.class);

			jobDelta.setMapOutputKeyClass(Text.class);
			jobDelta.setMapOutputValueClass(Text.class);

			jobDelta.setOutputKeyClass(Text.class);
			jobDelta.setOutputValueClass(Text.class);

			jobDelta.setInputFormatClass(NLineInputFormat.class);
			jobDelta.setOutputFormatClass(TextOutputFormat.class);
			jobDelta.getConfiguration().setInt("mapred.input.lineinputformat.linespermap", 30000);

			if(i == 0) TextInputFormat.addInputPath(jobDelta, new Path(args[1], "adjList"));
			else TextInputFormat.addInputPath(jobDelta, new Path(args[1], "Iter" + (i-1) + "_pr"));

			TextOutputFormat.setOutputPath(jobDelta, new Path(args[1], "Iter" + i + "_delta"));

			jobDelta.waitForCompletion(true);

			//********************END DELTA JOB**************************
			double tempDelta = 0D; // (double) jobDelta.getCounters().findCounter(DeltaCounter.Delta).getValue();
			delta = tempDelta / offSet;
			logger.info("*************************************************");
			logger.info("Delta: Iter end:" + i + " -- " + delta);

			//********************START PR JOB***************************
			// MR Job for Page Rank Calculation
			jobPR = Job.getInstance(conf, ("PR_Job" + (i+1)));
			jobPR.setJarByClass(FreqItemsetMining.class);

			final Configuration jobPRConf = jobPR.getConfiguration();
			jobPRConf.set("mapreduce.output.textoutputformat.separator", "\t");
			jobPRConf.set("DELTA", String.valueOf(delta));

			jobPR.setMapperClass(PRMapper.class);
			jobPR.setReducerClass(PRReducer.class);

			jobPR.setMapOutputKeyClass(Text.class);
			jobPR.setMapOutputValueClass(Text.class);

			jobPR.setOutputKeyClass(Text.class);
			jobPR.setOutputValueClass(Text.class);

			jobPR.setInputFormatClass(NLineInputFormat.class);
			jobPR.setOutputFormatClass(TextOutputFormat.class);
			jobDelta.getConfiguration().setInt("mapred.input.lineinputformat.linespermap", 30000);

			TextInputFormat.addInputPath(jobPR, new Path(args[1], "Iter" + i + "_delta"));
			TextOutputFormat.setOutputPath(jobPR, new Path(args[1], "Iter" + i +"_pr"));

			jobPR.waitForCompletion(true);
		}
		return 1;
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
