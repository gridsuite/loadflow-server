-- First truncate the child table
TRUNCATE TABLE limit_reduction_entity_reductions;

-- Then truncate the parent table
TRUNCATE TABLE limit_reduction_entity CASCADE;