wrk.method = "PUT"
wrk.body = "dd53e2b487da03fd02396306d248cda0"
local id = 0
request = function()
    local path = "/v0/entity?id=" .. id
    id = id + 1
    return wrk.format(nil, path)
end