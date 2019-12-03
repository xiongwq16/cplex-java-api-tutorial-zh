package examples;
/* --------------------------------------------------------------------------
 * File: CplexServer.java
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
 * CplexServer.java - Entering a problem using IloCplexModeler and
 *                    transferring it to another thread for solving
 */

import ilog.concert.*;
import ilog.cplex.*;
import java.io.*;


public class CplexServer {

   // define class to transfer model to server
   static class ModelData implements Serializable {
      private static final long serialVersionUID = 1021L;
      IloModel    model;
      IloNumVar[] vars;
      ModelData(IloModel m, IloNumVar[] v)
      {
         model = m; 
         vars  = v;
      }
   }

   // define class to transfer back solution
   static class SolutionData implements Serializable {
      private static final long serialVersionUID = 1022L;
      IloCplex.CplexStatus status;
      double               obj;
      double[]             vals;
   }

   public static void main(String[] args)
   {
      try {
         // setup pipe to transfer model to server
         PipedOutputStream mout = new PipedOutputStream();
         PipedInputStream  min  = new PipedInputStream(mout);

         // setup pipe to transfer results back
         PipedOutputStream sout = new PipedOutputStream();
         PipedInputStream  sin  = new PipedInputStream(sout);

         // start server
         new Server(min, sout).start();

         // build model
         IloNumVar[][] var = new IloNumVar[1][];
         IloRange[][]  rng = new IloRange[1][];

         IloCplexModeler model = new IloCplexModeler();
         populateByRow(model, var, rng);

         ObjectOutputStream os = new ObjectOutputStream(mout);
         os.writeObject(new ModelData(model, var[0]));

         ObjectInputStream  is = new ObjectInputStream(sin);
         SolutionData sol = (SolutionData)is.readObject();

         System.out.println("Solution status = " + sol.status);

         if ( sol.status.equals(IloCplex.CplexStatus.Optimal) ) {
            System.out.println("Solution value = " + sol.obj);
            int nvars = var[0].length;
            for (int j = 0; j < nvars; ++j)
               System.out.println("Variable " + j + ": Value = " + sol.vals[j]);
         }

         // signal that we're done
         os.writeObject(new ModelData(null, null));
         is.close();
      }
      catch (IloException e) {
         System.err.println("Concert exception '" + e + "' caught");
      }
      catch (Throwable t) {
         System.err.println("terminating due to exception " + t);
      }
   }


   // The following method populates the problem with data for the
   // following linear program:
   //
   //    Maximize
   //     x1 + 2 x2 + 3 x3
   //    Subject To
   //     - x1 + x2 + x3 <= 20
   //     x1 - 3 x2 + x3 <= 30
   //    Bounds
   //     0 <= x1 <= 40
   //    End
   //
   // using the IloModeler API

   static void populateByRow(IloModeler model,
                             IloNumVar[][] var,
                             IloRange[][] rng) throws IloException
   {
      double[]    lb      = {0.0, 0.0, 0.0};
      double[]    ub      = {40.0, Double.MAX_VALUE, Double.MAX_VALUE};
      String[]    varname = {"x1", "x2", "x3"};
      IloNumVar[] x       = model.numVarArray(3, lb, ub, varname);
      var[0] = x;

      double[] objvals = {1.0, 2.0, 3.0};
      model.addMaximize(model.scalProd(x, objvals));

      rng[0] = new IloRange[2];
      rng[0][0] = model.addLe(model.sum(model.prod(-1.0, x[0]),
                                        model.prod( 1.0, x[1]),
                                        model.prod( 1.0, x[2])), 20.0, "c1");
      rng[0][1] = model.addLe(model.sum(model.prod( 1.0, x[0]),
                                        model.prod(-3.0, x[1]),
                                        model.prod( 1.0, x[2])), 30.0, "c2");
   }


   // The server class
   static class Server extends Thread {
      PipedInputStream  pin;
      PipedOutputStream pout;

      Server(PipedInputStream in, PipedOutputStream out)
      {
         pin  = in;
         pout = out;
      }

      public void run()
      {
         try {
            ObjectInputStream  is = new ObjectInputStream(pin);
            ObjectOutputStream os = new ObjectOutputStream(pout);
            while ( true ) {
               ModelData data = (ModelData)is.readObject();
               
               if ( data.model == null ) {
                  is.close();
                  return;
               }
               IloCplex cplex = new IloCplex();
               cplex.setModel(data.model);

               SolutionData sol = new SolutionData();
               if ( cplex.solve() ) {
                  sol.obj  = cplex.getObjValue();
                  sol.vals = cplex.getValues(data.vars);
               }
               sol.status = cplex.getCplexStatus();
               os.writeObject(sol);

               cplex.end();
            }
         }
         catch (Throwable t) {
            System.err.println("server terminates due to " + t);
         }
      }
   }
}
