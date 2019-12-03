package examples;

/* --------------------------------------------------------------------------
 * File: LPex7.java
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
 * LPex7.java - Reading in and optimizing a problem.  Printing
 *              names with the answer.  This is a modification of
 *              LPex2.java
 *
 * To run this example, command line arguments are required.
 * i.e.,   java LPex7   filename   method
 * where 
 *     filename is the name of the file, with .mps, .lp, or .sav extension
 *     method   is the optimization method
 *                 o          default
 *                 p          primal simplex
 *                 d          dual   simplex
 *                 h          barrier with crossover
 *                 b          barrier without crossover
 *                 n          network with dual simplex cleanup
 *                 s          sifting
 *                 c          concurrent
 * Example:
 *     java LPex7  example.mps  o
 */

import ilog.concert.*;
import ilog.cplex.*;

public class LPex7 {
    static void usage() {
        System.out.println("usage:  LPex7 <filename> <method>");
        System.out.println("          o       default");
        System.out.println("          p       primal simplex");
        System.out.println("          d       dual   simplex");
        System.out.println("          h       barrier with crossover");
        System.out.println("          b       barrier without crossover");
        System.out.println("          n       network with dual simplex cleanup");
        System.out.println("          s       sifting");
        System.out.println("          c       concurrent");
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            usage();
            return;
        }
        try {
            // Create the modeler/solver object
            IloCplex cplex = new IloCplex();
            
            // 设置求解初始松弛问题的算法
            // Evaluate command line option and set optimization method accordingly.
            switch (args[1].charAt(0)) {
            case 'o':
                cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Auto);
                break;
            case 'p':
                cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Primal);
                break;
            case 'd':
                cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Dual);
                break;
            case 'h':
                cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Barrier);
                break;
            case 'b':
                cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Barrier);
                cplex.setParam(IloCplex.Param.Barrier.Crossover, IloCplex.Algorithm.None);
                break;
            case 'n':
                cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Network);
                break;
            case 's':
                cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Sifting);
                break;
            case 'c':
                cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Concurrent);
                break;
            default:
                usage();
                return;
            }

            // Read model from file with name args[0] into cplex optimizer object
            cplex.importModel(args[0]);

            // Solve the model and display the solution if one was found
            if (cplex.solve()) {
                System.out.println("Solution status = " + cplex.getStatus());
                System.out.println("Solution value  = " + cplex.getObjValue());

                // Access the IloLPMatrix object that has been read from a file in
                // order to access variables which are the columns of the LP. The
                // method importModel() guarantees that exactly one IloLPMatrix
                // object will exist, which is why no tests or iterators are
                // needed in the following line of code.
                IloLPMatrix lp = (IloLPMatrix) cplex.LPMatrixIterator().next();
                IloNumVar[] vars = lp.getNumVars();
                double[] vals = cplex.getValues(vars);

                // Basis information is not available for barrier without crossover.
                // Thus we include the query in a try statement and print the solution
                // without barrier information in case we get an exception.
                try {
                    IloCplex.BasisStatus[] bStat = cplex.getBasisStatuses(vars);
                    for (int i = 0; i < vals.length; i++) {
                        System.out.println(
                                "Variable " + vars[i].getName() + " has value " + vals[i] + " and status " + bStat[i]);
                    }
                } catch (IloException e) {
                    for (int i = 0; i < vals.length; i++) {
                        System.out.println("Variable " + vars[i].getName() + " has value " + vals[i]);
                    }
                }
            }
            cplex.end();
        } catch (IloException exc) {
            System.err.println("Concert exception '" + exc + "' caught");
        }
    }
}
