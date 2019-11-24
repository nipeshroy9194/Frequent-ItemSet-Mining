package preprocess;

import java.util.StringTokenizer;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class DatasetPreProcessing extends Configured implements Tool {
	private static final Logger logger = LogManager.getLogger(DatasetPreProcessing.class);

	/*
	 * Mapper emits the order id as the key and product id as the order
	 */
	public static class InitialMapper extends Mapper<Object, Text, Text, Text> {
		@Override
		public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
			final StringTokenizer itr = new StringTokenizer(value.toString(), ",");
			while (itr.hasMoreTokens()) {
				String order_id = itr.nextToken();
                String product_id = itr.nextToken();
//                itr.nextToken();
//                itr.nextToken();
				context.write(new Text(order_id),new Text(product_id));
			}
		}
	}

    /*
     * Reducer emits the order id and the list of product ids
     */
    public static class InitialReducer extends Reducer<Text, Text, Text, Text> {
		@Override
		public void reduce(final Text key, final Iterable<Text> values, final Context context)
                throws IOException, InterruptedException {
			StringBuilder output = new StringBuilder();
			String prefix = "";
			for (final Text val : values) {
				output.append(prefix);
				prefix = ",";
				output.append(val.toString());
			}

			context.write(new Text(key.toString()), new Text(output.toString()));
		}
	}


	@Override
	public int run(final String[] args) throws Exception {
        /*
         * First Map-Reduce Job - To pre-process dataset and generate ItemSet lists
         */
		final Configuration conf = getConf();
		Job job1 = Job.getInstance(conf, "DatasetPreProcessing");
		job1.setJarByClass(DatasetPreProcessing.class);

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

        FileInputFormat.addInputPath(job1, new Path(args[0], "transactions.csv"));
        FileOutputFormat.setOutputPath(job1, new Path(args[1], "PreProcessedData"));

		job1.waitForCompletion(true);

		return 1;
	}

	public static void main(final String[] args) {
		if (args.length != 2) {
			throw new Error("Two arguments required:\n<input-dir> <output-dir>");
		}

		try {
			ToolRunner.run(new DatasetPreProcessing(), args);
		} catch (final Exception e) {
			logger.error("", e);
		}
	}
}