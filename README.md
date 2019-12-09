# CS6240 - Project Group 7
1. Nipesh Roy
2. Burhan Sadilwala
3. Xiang Wei

# Project - Frequency Itemset Mining using Apriori Pruning
Background：Finding frequent itemsets is one of the most important fields of data mining. Apriori is an algorithm for frequent itemset mining and association rule learning over relational databases, proposed by R.Agrawal and R.Srikant in 1994[1]. Apriori algorithm scans the entire dataset many times and generates candidate itemsets if they appear in the transactions which are then pruned based on their frequencies if they do not satisfy a certain support threshold. The algorithm has very high memory usage and computational cost due to the fact that k item subsets are generated from a given list of items. This algorithm is very useful for market basket analysis and it enables online shopping websites to give suggestions like items frequently bought together, recommending similar items through advertisements, etc. Parallel and distributed computing are effective methods to solve this problem and improve the algorithms performance. This project will implement Frequent Itemset Mining using Apriori Pruning Algorithm based on Hadoop-MapReduce using the k-phase MR model.


Dataset used - https://www.instacart.com/datasets/grocery-shopping-2017


# Citation:
“The Instacart Online Grocery Shopping Dataset 2017”, Accessed from https://www.instacart.com/datasets/grocery-shopping-2017 on 11/24/2019

