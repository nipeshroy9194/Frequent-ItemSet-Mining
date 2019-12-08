package fim;

import java.util.*;
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
	private static int K = 2;

	// ***************************************************************************
	// First Map-Reduce Job - Computes Itemset Frequency for k = 1
	/**
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
			if(sum > 3) {
				StringBuilder sb = new StringBuilder("(").append(key.toString()).append(")");
				context.write(new Text(sb.toString()), new IntWritable(sum));
			}
		}
	}

	// ***************************************************************************
	// First Map-Reduce Job - Computes Itemset Frequency for k = 1
	/**
	 * This class defines a mapper which consumes the output files generated from previous iteration.
	 */
	public static class SecondMapper extends Mapper<Object, Text, Text, IntWritable> {
		List<Integer[]> cached = new ArrayList<>();

		// TODO: Handle file cache
//		@Override
//		protected void setup(final Context context) throws IOException, InterruptedException {
//			URI[] cacheFiles = context.getCacheFiles();
//			if (cacheFiles != null && cacheFiles.length > 0) {
//				Path path = new Path(cacheFiles[0].toString());
//				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path.getName())));
//				String line = "";
//				while ((line = br.readLine()) != null) {
//					String X = line.split(",")[0];
//					String Y = line.split(",")[1];
//
//					if (Integer.parseInt(X) <= MAX && Integer.parseInt(Y) <= MAX)
//						cached.add(line.trim()); // All relationships (under MAX_filter) are added to HashSet.
//				}
//				br.close();
//			}
//		}
		/**
		 *
		 */
		@Override
		public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
			final StringTokenizer itr = new StringTokenizer(value.toString());
			final Set<Integer[]> candidates = this.generateCandidates();

			while (itr.hasMoreTokens()) {
				Integer[] items = this.stringToArray(itr.nextToken());
				String token = itr.nextToken();
				Arrays.sort(items);
				Set<Integer[]> itemSets = this.generateItemsets(items, candidates.iterator().next().length);
				for(Integer[] itemSet: itemSets) {
					if(candidates.contains(itemSet))
						context.write(new Text(this.arrayToString(itemSet)), new IntWritable(1));
				}
			}
		}

		private Integer[] stringToArray(String itemString) {
			List<Integer> tempList = new ArrayList<>();
			itemString = itemString.replace("(", "").replace(")", "");
			for(String item: itemString.split(","))
				tempList.add(Integer.parseInt(item));
			return tempList.toArray(new Integer[0]);
		}

		private String arrayToString(Integer[] items) {
			StringBuilder sb = new StringBuilder("(");
			for(Integer item: items)
				sb.append(item).append(",");
			sb.deleteCharAt(sb.length() - 1);
			sb.append(")");
			return sb.toString();
		}

		private Set<Integer[]> generateCandidates() {
		    Set<Integer[]> tempCache = new TreeSet<>(new Comparator<Integer[]>() {
                @Override
                public int compare(Integer[] o1, Integer[] o2) {
                    for(int i=0; i<o1.length; i++) {
                        if(o1[i]==o2[i]) continue;
                        return o1[i].compareTo(o2[i]);
                    }
                    return 0;
                }
            });
		    int common = cached.get(0).length-1;

            for(int i=0; i<cached.size();i++)
                for(int j=i+1; j<cached.size(); j++) {
                    Integer[] c1 = cached.get(i);
                    Integer[] c2 = cached.get(j);
                    boolean flag = true;
                    for(int k=0; k<common; k++) {
                        if(c1[k] != c2[k]) {
                            flag = false;
                            break;
                        }
                    }

                    if(flag) {
                        Integer[] tempCandidate = new Integer[common+2];
                        for(int k=0; k<c1.length; k++)
                            tempCandidate[k] = c1[k];
                        tempCandidate[tempCandidate.length-1] = c2[c2.length-1];
                        Arrays.sort(tempCandidate);
                        tempCache.add(tempCandidate);
                    }
                }
			return tempCache;
		}

		private Set<Integer[]> generateItemsets(Integer[] items, int k) {
		    Set<Integer[]> itemSet= new HashSet<>();
            Integer[] data = new Integer[k];
            createCombinations(items, items.length, k, 0, data, 0, itemSet);

            return itemSet;
        }

        static void createCombinations(Integer[] items, int N, int K, int index, Integer[] set, int i, Set<Integer[]> itemSet)
        {
            if (index == K) {
                itemSet.add(set.clone());
                return;
            }
            if (i >= N) return;

            set[index] = items[i];
            createCombinations(items, N, K, index + 1, set, i + 1, itemSet);
            createCombinations(items, N, K, index, set, i + 1, itemSet);
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

		job.waitForCompletion(true);


		// TODO: Add distributed cache code
		while(K-- > 1) {
			final Configuration conf2 = getConf();
			job = Job.getInstance(conf2, "K1");
			job.setJarByClass(FreqItemsetMining.class);

			final Configuration jobConf2 = job.getConfiguration();
			jobConf2.set("mapreduce.output.textoutputformat.separator", "\t");

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

			job.waitForCompletion(true);
		}

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
