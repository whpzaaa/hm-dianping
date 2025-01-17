if(redis.call("get",KEY[1]) == ARGS[1]) then
    return redis.call("del",KEY[1])
end
return 0