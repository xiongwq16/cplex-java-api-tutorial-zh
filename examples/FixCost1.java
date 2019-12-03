package examples;
/* --------------------------------------------------------------------------
 * File: FixCost1.java
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
 * Problem Description
 * -------------------
 * 
 * A company must produce a product on a set of machines.
 * Each machine has limited capacity.
 * Producing a product on a machine has both a fixed cost
 * and a cost per unit of production.
 * 
 * Minimize the sum of fixed and variable costs so that the
 * company exactly meets demand.
 */

import ilog.concert.*;
import ilog.cplex.*;

public class FixCost1 {
    static int _nbMachines = 6;
    static double[] _cost = { 15.0, 20.0, 45.0, 64.0, 12.0, 56.0 };
    static double[] _capacity = { 100.0, 20.0, 405.0, 264.0, 12.0, 256.0 };
    static double[] _fixedCost = { 1900.0, 820.0, 805.0, 464.0, 3912.0, 556.0 };
    static double _demand = 22.0;

    public static void main(String[] args) {
        try {
            IloCplex cplex = new IloCplex();

            IloNumVar[] fused = cplex.boolVarArray(_nbMachines);
            IloNumVar[] x = cplex.numVarArray(_nbMachines, 0.0, Double.MAX_VALUE);

            // Objective: minimize the sum of fixed and variable costs
            cplex.addMinimize(cplex.sum(cplex.scalProd(_cost, x), cplex.scalProd(fused, _fixedCost)));

            for (int i = 0; i < _nbMachines; i++) {
                // Constraint: respect capacity constraint on machine 'i'
                cplex.addLe(x[i], _capacity[i]);

                // Constraint: only produce product on machine 'i' if it is 'used'
                // (to capture fixed cost of using machine 'i')
                cplex.addLe(x[i], cplex.prod(10000, fused[i]));
            }

            // Constraint: meet demand
            cplex.addEq(cplex.sum(x), _demand);

            if (cplex.solve()) {
                System.out.println("Solution status: " + cplex.getStatus());
                System.out.println("Obj " + cplex.getObjValue());
                // 完整性容错，指定整数变量可与某个整数相差且视为可行的偏差
                double eps = cplex.getParam(IloCplex.Param.MIP.Tolerances.Integrality);
                for (int i = 0; i < _nbMachines; i++)
                    if (cplex.getValue(fused[i]) > eps)
                        System.out.println("E" + i + " is used for " + cplex.getValue(x[i]));

                System.out.println();
                System.out.println("----------------------------------------");
            }
            cplex.end();
        } catch (IloException exc) {
            System.err.println("Concert exception '" + exc + "' caught");
        }
    }
}

/*
 * Solution Obj 1788 E5 is used for 22
 */
