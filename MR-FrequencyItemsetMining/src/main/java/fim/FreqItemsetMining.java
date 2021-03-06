package fim;

import java.io.*;
import java.net.URI;
import java.util.*;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * Map Reduce implementation for Frequent Itemset Mining
 */
public class FreqItemsetMining extends Configured implements Tool {
	private static final Logger logger = LogManager.getLogger(FreqItemsetMining.class);

	// ***************************************************************************
	// First Map-Reduce Job - Computes Itemset Frequency for k = 1
	/**
	 * This class defines a mapper which consumes the pre-processed input files generated by the previous MR Job.
	 */
	public static class InitialMapper extends Mapper<Object, Text, Text, IntWritable> {
		/**
		 * Emits the product id and a count of 1 for every time the product is found
		 */
		@Override
		public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
			final StringTokenizer itr = new StringTokenizer(value.toString());
			while (itr.hasMoreTokens()) {
				itr.nextToken();
				String[] items = itr.nextToken().split(",");
				for(String item: items)
					context.write(new Text(item), new IntWritable(1));
			}
		}
	}

	/**
	 * This class defines a combiner which just sums up partial count for each itemset.
	 */
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

	/**
	 * This class defines a reducer which calculates the final counts for each itemset.
	 * The itemsets are then pruned based on min_support, and (K+1)th iteration receives
	 * the pruned output from (K)th iteration.
	 */
	public static class InitialReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
		/* Reducer calculate product id and there frequencies */
		@Override
		public void reduce(final Text key, final Iterable<IntWritable> values, final Context context) throws IOException, InterruptedException {
			int sum = 0;
			for(final IntWritable c : values)
				sum += c.get();

			//MinSupport for pruning
			if(sum > 20_000) {
				String str = "("+ key.toString() + ")";
				context.write(new Text(str), new IntWritable(sum));
			}
		}
	}

	// ***************************************************************************
	// Second Map-Reduce Job - Computes Itemset Frequency for k>1.
	// Reducer is the same as for (k=1).
	/**
	 * This class defines a mapper which consumes the output files generated from previous iteration.
	 */
	public static class SecondMapper extends Mapper<Object, Text, Text, IntWritable> {
		List<Integer[]> cached = new ArrayList<>();

		/**
		 * Setup for reading broadcast (K-1)th output in memory for RepJoin
		 */
		@Override
		protected void setup(final Context context) throws IOException {
			Path[] cacheFiles = context.getLocalCacheFiles();
			if(cacheFiles != null && cacheFiles.length > 0) {
				for(Path cacheFile : cacheFiles) {
					logger.info("Reading file: " + cacheFile.toString());
					readFile(cacheFile);
				}
			}
		}

		/**
		 * Reads each file from the previous output folder and adds data to cache.
		 * @param cacheFile File to be loaded in cache
		 */
		private void readFile(Path cacheFile) throws IOException {
			System.out.println(cacheFile.toString());
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile.getName())));
			String line;
			while ((line = br.readLine()) != null) {
				final StringTokenizer itr = new StringTokenizer(line);
				while (itr.hasMoreTokens()) {
					String key = itr.nextToken();
					itr.nextToken();
					cached.add(stringToArray(key));
				}
			}
			br.close();
		}

		/**
		 * Mapper for K>1
		 * Generates candidates from previous output and emits transaction itemset is a candidate.
		 */
		@Override
		public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {
			final StringTokenizer itr = new StringTokenizer(value.toString());
			final Set<Integer[]> candidates = this.generateCandidates();
			Iterator<Integer[]> itr3 = candidates.iterator();
			while(itr3.hasNext()){
				System.out.print("Candidate: ");
				for(Integer in: itr3.next()) {
					System.out.print(in + ",");
				}
				System.out.println();
			}

			while (itr.hasMoreTokens()) {
				itr.nextToken();
				Integer[] items = this.stringToArray(itr.nextToken());
				Arrays.sort(items);
				Iterator<Integer[]> itrC = candidates.iterator();
				int K = 0;
				while(itrC.hasNext()) {
					Integer[] c = itrC.next();
					if(c != null) {
						K = c.length;
						break;
					}
				}

				Set<Integer[]> itemSets = this.generateItemsets(items, K);
				for(Integer[] itemSet: itemSets) {
					if(candidates.contains(itemSet))
						context.write(new Text(this.arrayToString(itemSet)), new IntWritable(1));
				}
			}
		}

		/**
		 * Converts a string to array of integers, separating on ","
		 */
		private Integer[] stringToArray(String itemString) {
			List<Integer> tempList = new ArrayList<>();
			itemString = itemString.trim().replace("(", "").replace(")", "");
			for(String item: itemString.split(","))
				tempList.add(Integer.parseInt(item));
			return tempList.toArray(new Integer[0]);
		}

		/**
		 * Converts an array of integers to one string
		 */
		private String arrayToString(Integer[] items) {
			StringBuilder sb = new StringBuilder();
			for(Integer item: items)
				sb.append(item).append(",");
			sb.deleteCharAt(sb.length() - 1);
			return sb.toString();
		}

		/**
		 *
		 * @return a set of all possible distinct candidate itemsets of size K.
		 */
		private Set<Integer[]> generateCandidates() {
			Set<Integer[]> tempCache = new TreeSet<>((Integer[] o1, Integer[] o2) -> {
				for(int i=0; i<o1.length; i++) {
					if(o1[i].equals(o2[i])) continue;
					return o1[i].compareTo(o2[i]);
				}
				return 0;
			});
			int common = cached.size() > 0 ? cached.get(0).length-1 : 0;

			for(int i=0; i<cached.size();i++)
				for(int j=i+1; j<cached.size(); j++) {
					Integer[] c1 = cached.get(i);
					Integer[] c2 = cached.get(j);
					boolean flag = true;
					for(int k=0; k<common; k++) {
						if(!c1[k].equals(c2[k])) {
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

		/**
		 * Generates all K sized itemsets from a given transaction
		 * @param items transaction itemset
		 * @param k K
		 * @return a set of all possible distinct K-sized itemsets
		 */
		private Set<Integer[]> generateItemsets(Integer[] items, int k) {
			Set<Integer[]> itemSet= new HashSet<>();
			Integer[] data = new Integer[k];
			createCombinations(items, items.length, k, 0, data, 0, itemSet);

			return itemSet;
		}

		/**
		 * Helper function to recursively generates all K sized itemsets from a given transaction.
		 */
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
		int K = 3;
		final Configuration conf = getConf();
		Job job = Job.getInstance(conf, "K1");
		job.setJarByClass(FreqItemsetMining.class);

		final Configuration jobConf = job.getConfiguration();
		jobConf.set("mapreduce.output.textoutputformat.separator", "\t");

		job.setMapperClass(InitialMapper.class);
		job.setCombinerClass(InitialCombiner.class);
		job.setReducerClass(InitialReducer.class);

		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);

		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);

		TextInputFormat.addInputPath(job, new Path(args[0]));
		TextOutputFormat.setOutputPath(job, new Path(args[1], "K1"));

		job.waitForCompletion(true);

		int i = 2;
		int jobStatus = 0;

		// ***************************************************************************
		// Loop MR job for K-1 iterations
		while(i <= K) {
			int prevItr = i - 1;
			String prevPath = "K" + prevItr;
			String currPath = "K" + i;

			final Job job1 = Job.getInstance(conf, currPath);
			job1.setJarByClass(FreqItemsetMining.class);
			final Configuration jobConf1 = job1.getConfiguration();
			jobConf1.set("mapreduce.output.text.outputformat.separator", "\t");


			// Cached file passed as program argument
			Path prevFolder = new Path(args[1], prevPath);

			FileSystem fs = FileSystem.get(jobConf1);
			FileStatus[] fileStatus = fs.listStatus(prevFolder);
			for (FileStatus status : fileStatus) {
				String filePath = status.getPath().toString();
				if (!filePath.contains("SUCCESS") && !filePath.contains(".crc")) {
					job1.addCacheFile(new URI(status.getPath().toString()));
				}
			}


			job1.setMapperClass(SecondMapper.class);
			job1.setCombinerClass(InitialCombiner.class);
			job1.setReducerClass(InitialReducer.class);

			job1.setOutputKeyClass(Text.class);
			job1.setOutputValueClass(IntWritable.class);

			TextInputFormat.addInputPath(job1, new Path(args[0]));
			if(i!=K)
				TextOutputFormat.setOutputPath(job1, new Path(args[1], currPath));
			else
				TextOutputFormat.setOutputPath(job1, new Path(args[2]));

			jobStatus = job1.waitForCompletion(true) ? 0 : 1;
			i++;
		}
		return jobStatus;
	}


	public static void main(final String[] args) {
		if (args.length != 3) {
			throw new Error("Three arguments required:\n<input-dir> <intermediate-dir> <output-dir>");
		}

		try {
			ToolRunner.run(new FreqItemsetMining(), args);
		} catch (final Exception e) {
			logger.error("", e);
		}
	}
}
