package examples;
/* --------------------------------------------------------------------------
 * File: Etsp.java
 * Version 12.9.0  
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2001, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 *
 * Etsp.java - Solving an earliness-tardiness scheduling problem
 *             using CPLEX linearization capabilities.
 *
 * A command line argument indicating the input data file is required
 * to run this example.
 *
 *     java Etsp ../../../examples/data/etsp.dat
 */

import ilog.concert.*;
import ilog.cplex.*;

public class Etsp {
   static private int Horizon = 10000;

   static private class Data {
      int        nJobs;
      int        nResources;
      int[][]    activityOnResource;
      double[][] duration;
      double[]   dueDate;
      double[]   earlinessCost;
      double[]   tardinessCost;

      Data(String filename) throws IloException, java.io.IOException,
                                   InputDataReader.InputDataReaderException
      {
         InputDataReader reader = new InputDataReader(filename);

         activityOnResource = reader.readIntArrayArray();
         duration           = reader.readDoubleArrayArray();
         dueDate            = reader.readDoubleArray();
         earlinessCost      = reader.readDoubleArray();
         tardinessCost      = reader.readDoubleArray();

         nJobs      = dueDate.length;
         nResources = activityOnResource.length;
      }
   }

   public static void main (String args[]) {
      try {
         String filename;
         if ( args.length > 1)  filename = args[0];
         else                   filename = "./data/etsp.dat";

         Data     data  = new Data(filename);
         IloCplex cplex = new IloCplex();

         // Create start variables
         IloNumVar[][] s = new IloNumVar[data.nJobs][];
         for (int j = 0; j < data.nJobs; j++)
            s[j] = cplex.numVarArray(data.nResources, 0.0, Horizon);

         // State precedence constraints
         for (int j = 0; j < data.nJobs; j++) {
            for (int i = 1; i < data.nResources; i++)
               cplex.addGe(s[j][i], cplex.sum(s[j][i-1], data.duration[j][i-1]));
         }

         // State disjunctive constraints for each resource
         for (int i = 0; i < data.nResources; i++) {
            int end = data.nJobs - 1;
            for (int j = 0; j < end; j++) {
               int a = data.activityOnResource[i][j];
               for (int k = j + 1; k < data.nJobs; k++) {
                  int b = data.activityOnResource[i][k];
                  cplex.add(cplex.or(
                     cplex.ge(s[j][a], cplex.sum(s[k][b], data.duration[k][b])),
                     cplex.ge(s[k][b], cplex.sum(s[j][a], data.duration[j][a]))
                  ));
               }
            }
         }

         // The cost is the sum of earliness or tardiness costs of each job 
         int last = data.nResources - 1;
         IloNumExpr costSum = cplex.numExpr();
         for (int j = 0; j < data.nJobs; j++) {
            double[] points = { data.dueDate[j] };
            double[] slopes = { -data.earlinessCost[j],
                                data.tardinessCost[j] };
            costSum = cplex.sum(costSum,
               cplex.piecewiseLinear(
                  cplex.sum(s[j][last], data.duration[j][last]),
                  points, slopes, data.dueDate[j], 0)
            );
         }
         cplex.addMinimize(costSum);
         
         /*
          * 控制 MIP 中速度、可行性、最优性和移动界限之间的折衷
          * 0: 在使用缺省设置 BALANCED 时，CPLEX 致力于快速证明最佳解法，但要均衡在优化早期找到高质量可行解法
          * 1: 在将此参数设置为 FEASIBILITY 时，CPLEX 将在优化问题时频繁生成更多可行的解法，牺牲一部分最优性证明速度
          * 2: 在设置为 OPTIMALITY 时，早期阶段应用于查找可行的解法的工作量较少
          * 3: 使用设置 BESTBOUND 时，将通过移动最佳界限值来更着重强调证明最优性，因此顺便检测可行的解法几乎成为成为偶然
          * 4: 在参数设置为 HIDDENFEAS 时，MIP 优化器更努力查找非常难于找到的高质量可行的解法，因此在 FEASIBILITY 设置难于找到可接受质量的解法时考虑此设置。
          */
         cplex.setParam(IloCplex.Param.Emphasis.MIP, 4);

         if ( cplex.solve() ) {
             System.out.println("Solution status: " + cplex.getStatus());
             System.out.println(" Optimal Value = " + cplex.getObjValue());
         }
         cplex.end();
      }
      catch (IloException e) {
         System.err.println("Concert exception caught: " + e);
      }    
      catch (InputDataReader.InputDataReaderException ex) {
         System.out.println("Data Error: " + ex);
      }
      catch (java.io.IOException ex) {
         System.out.println("IO Error: " + ex);
      }
   }
}

