package examples;
// --------------------------------------------------------------------------
// File: FixNet.java
// Version 12.9.0
// --------------------------------------------------------------------------
// Licensed Materials - Property of IBM
// 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
// Copyright IBM Corporation 2018, 2019. All Rights Reserved.
//
// US Government Users Restricted Rights - Use, duplication or
// disclosure restricted by GSA ADP Schedule Contract with
// IBM Corp.
// --------------------------------------------------------------------------
//

import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;

/** FixNet.java - Use logical constraints to avoid numerical trouble in a
 *               fixed charge network flow problem.
 *
 *  Find a minimum cost flow in a fixed charge network flow problem.
 *  The network is as follows:
 * <pre>
 *       1 -- 3 ---> demand = 1,000,000
 *      / \  /       
 *     /   \/        
 *    0    /\        
 *     \  /  \       
 *      \/    \      
 *       2 -- 4 ---> demand = 1
 * </pre>
 *  A fixed charge of one is incurred for every edge with non-zero flow,
 *  with the exception of edge <1,4>, which has a fixed charge of ten.
 *  The cost per unit of flow on an edge is zero, with the exception of
 *  edge <2,4>, where the cost per unit of flow is five.
 */

public final class FixNet {

   // Define origin and destination nodes for each edge, as well as
   // unit costs and fixed costs for transporting flow on each edge.
   // Note that by defining a fixed cost of 0 for each arc you just
   // get a regular min-cost flow problem.
   private static final int[] orig = new int[]{0,0,1,1,2,2};
   private static final int[] dest = new int[]{1,2,3,4,3,4};
   private static final double[] unitcost = new double[]{0,0,0,0,0,5};
   private static final double[] fixedcost = new double[]{1,1,1,10,1,1};
   
   // Define demand (supply) at each node.
   private static final double[] demand = new double[]{-1000001, 0, 0, 1000000, 1};

   public static void main(String[] args) throws IloException {
      final IloCplex cplex = new IloCplex();

      try {
         // Create the variables.
         // x variables are continuous variables in [0, infinity[,
         // f variables are binary variables.
         IloNumVar[] x = cplex.numVarArray(orig.length, 0.0, Double.POSITIVE_INFINITY);
         for (int i = 0; i < x.length; ++i)
            x[i].setName(String.format("x%d%d", orig[i], dest[i]));
         IloNumVar[] f = cplex.boolVarArray(orig.length);
         for (int i = 0; i < f.length; ++i)
            x[i].setName(String.format("f%d%d", orig[i], dest[i]));

         // Create objective function.
         cplex.addMinimize(cplex.sum(cplex.scalProd(unitcost, x),
                                     cplex.scalProd(fixedcost, f)));

         // Create constraints.
         // There is one constraint for each node. The constraint for a node i
         // states that the flow leaving i and the flow entering i must differ
         // by at least the demand for i.
         for (int i = 0; i < demand.length; ++i) {
            IloLinearNumExpr sum = cplex.linearNumExpr();
            for (int j = 0; j < orig.length; ++j) {
               if ( orig[j] == i )
                  sum.addTerm(-1.0, x[j]);
               if ( dest[j] == i)
                  sum.addTerm(+1.0, x[j]);
            }
            cplex.addGe(sum, demand[i]);
         }

         // Add logical constraints that require x[i]==0 if f[i] is 0.
         for (int i = 0; i < orig.length; ++i)
            cplex.add(cplex.ifThen(cplex.eq(f[i], 0.0), cplex.eq(x[i], 0.0)));

         // Solve the problem.
         cplex.solve();

         // Write solution value and objective to the screen.
         System.out.println("Solution status: " + cplex.getStatus());
         System.out.println("Solution value  = " + cplex.getObjValue());
         System.out.println("Solution vector:");
         for (IloNumVar v : x)
            System.out.println(String.format("%10s: %15.6f", v.getName(), cplex.getValue(v)));
         for (IloNumVar v : f)
            System.out.println(String.format("%10s: %15.6f", v.getName(), cplex.getValue(v)));
         
         // Finally dump the model
         cplex.exportModel("FixNet.lp");
      }
      finally {
         cplex.end();
      }
   }
}

