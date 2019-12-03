package examples;
/* --------------------------------------------------------------------------
 * File: MIPex3.java
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
 * MIPex3.java - Entering and optimizing a MIP problem with SOS sets
 *               and priority orders.  It is a modification of MIPex1.java.
 *               Note that the problem solved is slightly different than
 *               MIPex1.java so that the output is interesting.
 */

/*
 * 第 1 类 SOS 是一个其中最多一个变量可以非零的变量集。
 * 第 2 类 SOS 是一个其中最多两个变量可以非零的变量集。 如果两个变量非零，那么这两个变量在该集合中必须相邻。
 */

import ilog.concert.*;
import ilog.cplex.*;
 
 
public class MIPex3 {
   public static void main(String[] args) {
      try {
         // create CPLEX optimizer/modeler and turn of presolve to make
         // the output more interesting
         IloCplex cplex = new IloCplex();
         cplex.setParam(IloCplex.Param.Preprocessing.Presolve, false);
       
         // build model
         IloNumVar[][] var = new IloNumVar[1][];
         IloRange[][]  rng = new IloRange[1][];
         populateByRow (cplex, var, rng);
       
         // setup branch priorities
         IloNumVar[] ordvar = {var[0][1], var[0][3]};
         int[]       ordpri = {8, 7};
         cplex.setPriorities (ordvar, ordpri);
       
         // setup branch directions
         cplex.setDirection(ordvar[0], IloCplex.BranchDirection.Up);
         cplex.setDirection(ordvar[1], IloCplex.BranchDirection.Down);
       
         // write priority order to file
         cplex.writeOrder("mipex3.ord");
       
         // optimize and output solution information
         if ( cplex.solve() ) {
            double[] x     = cplex.getValues(var[0]);
            double[] slack = cplex.getSlacks(rng[0]);
          
            System.out.println("Solution status = " + cplex.getStatus());
            System.out.println("Solution value  = " + cplex.getObjValue());
          
            for (int j = 0; j < x.length; ++j) {
               System.out.println("Variable " + j + ": Value = " + x[j]);
            }
          
            for (int i = 0; i < slack.length; ++i) {
               System.out.println("Constraint " + i + ": Slack = " + slack[i]);
            }
         }
         cplex.exportModel("mipex3.lp");
         cplex.end();
      }
      catch (IloException e) {
          System.err.println("Concert exception caught: " + e);
      }
   }

   static void populateByRow (IloMPModeler  model,
                              IloNumVar[][] var,
                              IloRange[][]  rng) throws IloException {

      // Define the variables one-by-one
      IloNumVar[] x = new IloNumVar[4];
      x[0] = model.numVar(0.0, 40.0, "x0");
      x[1] = model.intVar(0, Integer.MAX_VALUE, "x1");
      x[2] = model.intVar(0, Integer.MAX_VALUE, "x2");
      x[3] = model.intVar(2, 3, "x3");
      var[0] = x;
    
      // Objective Function
      model.addMaximize(model.sum(model.prod( 1.0, x[0]),
                                  model.prod( 2.0, x[1]),
                                  model.prod( 3.0, x[2]),
                                  model.prod( 1.0, x[3])));
    
      // Define three constraints one-by-one 
      rng[0] = new IloRange[3];
      rng[0][0] = model.addLe(model.sum(model.prod(-1.0, x[0]),
                                        model.prod( 1.0, x[1]),
                                        model.prod( 1.0, x[2]),
                                        model.prod(10.0, x[3])),
                              20.0, "rng0");
      rng[0][1] = model.addLe(model.sum(model.prod( 1.0, x[0]),
                                        model.prod(-3.0, x[1]),
                                        model.prod( 1.0, x[2])),
                              30.0, "rng1");
      rng[0][2] = model.addEq(model.sum(model.prod( 1.0, x[1]),
                                        model.prod(-3.5, x[3])),
                              0, "rng2");
    
      // add special ordered set of type 1
      IloNumVar[] sosvars    = {x[2], x[3]};
      double[]    sosweights = {25.0, 18.0};
      model.addSOS1(sosvars, sosweights);
   }
}
