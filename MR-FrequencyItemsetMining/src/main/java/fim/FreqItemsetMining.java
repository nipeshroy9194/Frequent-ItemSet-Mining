package fim;

import java.util.StringTokenizer;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
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

	// ***************************************************************************
	// First Map-Reduce Job - Computes Itemset Frequency for k = 1
	/*
	 * This class defines a mapper which consumes the pre-processed input files generated from first MR Job.
	 */
	public static class InitialMapper extends Mapper<Object, Text, Text, IntWritable> {
		/**
		 * Emits the product id and a count of 1 for every time the product is found
		 */
		@Override
		public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
			final StringTokenizer itr = new StringTokenizer(value.toString());
			while (itr.hasMoreTokens()) {
				String token = itr.nextToken();
				String[] items = itr.nextToken().split(",");
				for(String item: items)
					context.write(new Text(item), new IntWritable(1));
			}
		}
	}

	public static class InitialCombiner extends Reducer<Text, IntWritable, Text, IntWritable> {
		/* Combiner to reduce Mapper output to Reducers and calculate local counts */
		@Override
		public void reduce(final Text key, final Iterable<IntWritable> values, final Context context) throws IOException, InterruptedException {
			int partialSum = 0;
			for(final IntWritable c : values)
				partialSum += c.get();
			context.write(key, new IntWritable(partialSum));
		}
	}

	public static class InitialReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
		/* Reducer calculate product id and there frequencies */
		@Override
		public void reduce(final Text key, final Iterable<IntWritable> values, final Context context) throws IOException, InterruptedException {
			int sum = 0;
			for(final IntWritable c : values)
				sum += c.get();

			//MinSupport should be greater than 3 (Need to find a good min_support value)
			if(sum > 3)
				context.write(key, new IntWritable(sum));
		}
	}

	// ***************************************************************************

	@Override
	public int run(final String[] args) throws Exception {
		/* MR job to compute itemset frequencies for k = 1 */
		final Configuration conf = getConf();
		Job job = Job.getInstance(conf, "K1");
		job.setJarByClass(FreqItemsetMining.class);

		final Configuration jobConf = job.getConfiguration();
		jobConf.set("mapreduce.output.textoutputformat.separator", "\t");

		job.setMapperClass(InitialMapper.class);
		job.setCombinerClass(InitialCombiner.class);
		job.setReducerClass(InitialReducer.class);

//		job1.setMapOutputKeyClass(Text.class);
//		job1.setMapOutputValueClass(IntWritable.class);

		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);

		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);

		TextInputFormat.addInputPath(job, new Path(args[0]));
		TextOutputFormat.setOutputPath(job, new Path(args[1], "K1"));

		return job.waitForCompletion(true) ? 1 : 0;

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
